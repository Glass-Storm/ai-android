// Root build for the ai-android production library.
// The wave-0 `spike/` project is a SEPARATE Gradle build and is NOT part of this one.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// --- Engine dependency resolution gate -------------------------------------------------
// Every pinned engine artifact is resolved against the repositories declared in
// settings.gradle.kts. Resolving this configuration forces a real download/validation so a
// broken catalog entry fails here with a clear error instead of silently disappearing.
// The llama.cpp / `recombine` native binding is NOT resolvable (built locally in todo 17)
// and is therefore deliberately absent.
val engineResolution by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Resolves every pinned engine artifact against the declared repositories."
}

dependencies {
    add(engineResolution.name, libs.onnxruntime.android)
    add(engineResolution.name, libs.opencc4j)
    add(engineResolution.name, libs.litert)
    add(engineResolution.name, libs.mediapipe.tasks.vision)
    // sherpa-onnx ships on no public repository as a stable artifact: vendored AAR, resolved via the
    // `flatDir(libs/)` repository as a module dependency (a raw file dep breaks AAR assembly).
    add(
        engineResolution.name,
        mapOf("name" to "sherpa-onnx-${libs.versions.sherpaOnnx.get()}", "ext" to "aar"),
    )
}

tasks.register("verifyEngineResolution") {
    description = "Forces resolution of all pinned engine artifacts."
    val engineArtifacts = engineResolution
    doLast {
        val resolved = engineArtifacts.resolvedConfiguration.resolvedArtifacts
        resolved.sortedBy { it.file.name }.forEach {
            logger.lifecycle("RESOLVED ${it.moduleVersion.id} -> ${it.file.name}")
        }
    }
}

// --- Tier B device-conformance guard (todo 28) ------------------------------------------------
// `connectedAndroidTest` needs an attached device/emulator. AGP's connected test task fails hard
// (`DeviceException: No connected devices!`) when none is present, which would break CI. Gate every
// connected instrumentation task on a device probe: with no target attached the task is skipped and
// a documented `SKIPPED: no device` artifact is written instead of failing the build.
val tierBDeviceState = "device"

val adbExecutable: File? = run {
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    sdk?.let { dir -> File(dir, "platform-tools/adb").takeIf { it.canExecute() } }
}

fun connectedDeviceSerials(): List<String> {
    val adb = adbExecutable ?: return emptyList()
    return runCatching {
        val process = ProcessBuilder(adb.absolutePath, "devices").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
        process.waitFor()
        output.lineSequence()
            .drop(1)
            .map { line -> line.trim() }
            .filter { line -> line.endsWith(tierBDeviceState) }
            .map { line -> line.substringBefore('\t').trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()
    }.getOrDefault(emptyList())
}

fun recordTierBSkip(taskName: String) {
    val artifact = File(rootDir, "build/tier-b/device-conformance-skipped.md")
    artifact.parentFile?.mkdirs()
    artifact.writeText(
        "# Tier B device conformance - SKIPPED: no device\n\n" +
            "- task: $taskName\n" +
            "- reason: no adb device/emulator attached (`adb devices` empty)\n" +
            "- action: attach an arm64 device or start an x86_64 emulator and re-run `./gradlew $taskName`\n"
    )
    logger.lifecycle("SKIPPED: no device - $taskName (see ${artifact.relativeTo(rootDir)})")
}

gradle.projectsEvaluated {
    allprojects {
        tasks.matching { task ->
            task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
        }.configureEach {
            onlyIf { task ->
                val devices = connectedDeviceSerials()
                if (devices.isEmpty()) {
                    recordTierBSkip(task.name)
                    false
                } else {
                    logger.lifecycle("Tier B target(s): ${devices.joinToString()}")
                    true
                }
            }
        }
    }
}

// --- Native 16 KB ELF-alignment gate (todo 30) ---------------------------------------------
// Reuses :core's pure-JVM `ElfAlignmentVerifier` (no second ELF parser) to assert that every
// vendored `.so` shipped for the PRODUCTION ABI (`arm64-v8a`) is 16 KB (0x4000) PT_LOAD-aligned:
//   * every resolvable engine AAR (onnxruntime-android, litert, tasks-vision) + the vendored
//     sherpa-onnx AAR, read straight out of the AAR zip's `jni/arm64-v8a/`,
//   * the locally-built `:recombine` release AAR (llama.cpp / ggml / libomp / librecombine).
// The adversarial switch `-Ptask30.probeElfDir=<dir>` additionally verifies every `*.so` in `dir`, so
// a deliberately mis-aligned ELF can be shown to FAIL this task (QA failure scenario).
val productionAbi = "arm64-v8a"

tasks.register("verifyNativeAlignment") {
    group = "verification"
    description =
        "Asserts 16 KB (0x4000) PT_LOAD alignment of every vendored .so via core ElfAlignmentVerifier."
    dependsOn(":core:compileReleaseKotlin", ":recombine:assembleRelease")

    val engineArtifacts = engineResolution
    val coreClassesDir = project(":core").layout.buildDirectory.dir("tmp/kotlin-classes/release")
    val recombineAar = project(":recombine").layout.buildDirectory.file("outputs/aar/recombine-release.aar")
    val sherpaAar = rootProject.layout.projectDirectory.file(
        "libs/sherpa-onnx-${libs.versions.sherpaOnnx.get()}.aar"
    )
    val probeDirProvider = providers.gradleProperty("task30.probeElfDir")

    doLast {
        val coreDir = coreClassesDir.get().asFile
        check(coreDir.isDirectory) { "core release classes not found: $coreDir (did :core:compileReleaseKotlin run?)" }

        // Load the already-compiled :core verifier instead of re-implementing ELF parsing.
        val loader = java.net.URLClassLoader(arrayOf(coreDir.toURI().toURL()), javaClass.classLoader)
        val verifierClass = loader.loadClass("hk.edu.hkmu.core.native.ElfAlignmentVerifier")
        val verifier = verifierClass.getField("INSTANCE").get(null)
        val verifyBytes = verifierClass.getMethod("verify", ByteArray::class.java, String::class.java)

        val table = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var scanned = 0

        fun audit(source: String, entry: String, bytes: ByteArray) {
            scanned++
            val report = verifyBytes.invoke(verifier, bytes, "$source!$entry")
            val allAligned = report.javaClass.getMethod("getAll16KbAligned").invoke(report) as Boolean
            @Suppress("UNCHECKED_CAST")
            val segments = report.javaClass.getMethod("getSegments").invoke(report) as List<Any>
            val aligns = segments.joinToString(",") { seg ->
                "0x" + (seg.javaClass.getMethod("getAlign").invoke(seg) as Long).toString(16)
            }
            val verdict = if (allAligned) "OK" else "MISALIGNED"
            table += String.format("%-28s %-34s p_align=[%s] %s", source, entry, aligns, verdict)
            if (!allAligned) failures += "$source!$entry"
        }

        val aarFiles = LinkedHashSet<File>()
        runCatching { engineArtifacts.resolvedConfiguration.resolvedArtifacts }
            .getOrNull()
            ?.forEach { artifact -> if (artifact.file.name.endsWith(".aar")) aarFiles += artifact.file }
        aarFiles += sherpaAar.asFile
        aarFiles += recombineAar.get().asFile

        aarFiles.filter { it.isFile }.sortedBy { it.name }.forEach { aar ->
            java.util.zip.ZipFile(aar).use { zip ->
                zip.entries().asSequence()
                    .filter { entry -> entry.name.startsWith("jni/$productionAbi/") && entry.name.endsWith(".so") }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        audit(aar.name, entry.name.substringAfterLast('/'), zip.getInputStream(entry).readBytes())
                    }
            }
        }

        probeDirProvider.orNull?.let { dir ->
            val probe = File(dir)
            check(probe.isDirectory) { "task30.probeElfDir is not a directory: $probe" }
            probe.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.sortedBy { it.name }?.forEach { f ->
                audit("probe", f.name, f.readBytes())
            }
        }

        logger.lifecycle("=== 16 KB ELF alignment audit (ABI=$productionAbi) ===")
        table.forEach { logger.lifecycle(it) }
        logger.lifecycle("=== scanned $scanned .so file(s); ${failures.size} misaligned ===")
        check(scanned > 0) { "no .so files were scanned — alignment gate would be vacuous" }
        if (failures.isNotEmpty()) {
            throw GradleException("16 KB ELF alignment FAILED for: ${failures.joinToString()}")
        }
    }
}

