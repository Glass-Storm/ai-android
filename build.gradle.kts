// Root build for the ai-android production library.
// The wave-0 `spike/` project is a SEPARATE Gradle build and is NOT part of this one.
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
}

// --- API documentation (Dokka Gradle plugin v2, version pinned in the catalog) ------------------
// Every library module applies `org.jetbrains.dokka`; this root project aggregates their HTML into
// a single publication at build/dokka/html. `failOnWarning` makes every Dokka warning fatal, and
// `reportUndocumented` (opt-in via -Ptask31.reportUndocumented) turns an undocumented public
// declaration into such a warning, so the gate can be shown to be real.
// DGP v2 renamed the old `dokkaHtml` task to `dokkaGeneratePublicationHtml` and leaves `dokkaHtml`
// behind as a V1 compatibility stub whose action throws if executed. The stub is turned into a thin
// no-op that depends on the v2 publication task, so the documented `./gradlew dokkaHtml` command
// drives the real generator. Subproject stubs are neutralised the same way in their build scripts.
val reportUndocumentedProbe = providers.gradleProperty("task31.reportUndocumented").isPresent

dokka {
    dokkaPublications.html {
        moduleName.set("ai-android")
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        reportUndocumented.set(reportUndocumentedProbe)
    }
}

dependencies {
    dokka(project(":core"))
    dokka(project(":asr"))
    dokka(project(":tts"))
    dokka(project(":diarize"))
    dokka(project(":translate"))
    dokka(project(":sign"))
    dokka(project(":recombine"))
    dokka(project(":models"))
    dokka(project(":testkit"))
}

tasks.named("dokkaHtml") {
    actions.clear()
    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
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
    // Also runs the spike-absence packaging gate (todo 32) so the documented verification command
    // covers both packaging invariants.
    dependsOn("verifyNoSpikeInPublishedGraph")

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

// --- Spike-absence packaging gate (todo 32) --------------------------------------------------
// The wave-0 `spike/` build is throwaway and is deleted in this task. This gate makes that a
// mechanical invariant, not a convention:
//   * (graph) no subproject named like `spike` may be part of THIS Gradle build, and the on-disk
//     `spike/` tree must be gone;
//   * (packaging) no published release AAR may contain a `spike` path entry or a
//     `hk/edu/hkmu/spike` class.
// The adversarial switch `-Ptask32.probeSpikeAar=<file>` also audits an extra AAR, so a spike
// module leaking into a published artifact can be shown to FAIL this task (QA failure scenario).
val publishedModules = listOf("core", "asr", "tts", "diarize", "translate", "sign", "recombine", "models", "testkit")

tasks.register("verifyNoSpikeInPublishedGraph") {
    group = "verification"
    description = "Asserts no spike/ code is in the production Gradle graph or any published release AAR."
    dependsOn(publishedModules.map { ":$it:assembleRelease" })
    val probeAar = providers.gradleProperty("task32.probeSpikeAar")

    doLast {
        // (graph) no spike project, and the throwaway tree is deleted.
        val spikeProjects = rootProject.subprojects.filter { sub -> sub.name.contains("spike", ignoreCase = true) }
        check(spikeProjects.isEmpty()) {
            "spike project(s) leaked into the Gradle graph: ${spikeProjects.map { it.path }}"
        }
        val spikeDir = rootProject.file("spike")
        check(!spikeDir.exists()) { "throwaway spike/ tree still present at ${spikeDir.absolutePath}" }
        logger.lifecycle(
            "spike-absence[graph]: no 'spike' project among ${rootProject.subprojects.size} subprojects; " +
                "spike/ tree absent on disk"
        )

        // (packaging) no spike entry in any published release AAR.
        val aars = mutableListOf<File>()
        publishedModules
            .map { module -> rootProject.file("$module/build/outputs/aar/$module-release.aar") }
            .filter { file -> file.isFile }
            .forEach { aars += it }
        probeAar.orNull?.let { aars += File(it) }
        check(aars.isNotEmpty()) { "no release AARs found — spike-absence gate would be vacuous" }

        fun isSpikeEntry(entry: String): Boolean {
            val panic = entry.startsWith("hk/edu/hkmu/spike/")
            val segment = entry.split('/').any { seg -> seg.equals("spike", ignoreCase = true) }
            return panic || segment
        }

        // A spike module would land as `hk/edu/hkmu/spike/**` INSIDE the AAR's nested `classes.jar`,
        // so scan the nested jars, not just the top-level AAR entry names.
        fun spikeHits(aar: File): List<String> {
            val hits = mutableListOf<String>()
            java.util.zip.ZipFile(aar).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    if (name.endsWith(".jar") && !entry.isDirectory) {
                        java.util.zip.ZipInputStream(zip.getInputStream(entry)).use { nested ->
                            var nestedEntry = nested.nextEntry
                            while (nestedEntry != null) {
                                if (isSpikeEntry(nestedEntry.name)) {
                                    hits += "$name!${nestedEntry.name}"
                                }
                                nestedEntry = nested.nextEntry
                            }
                        }
                    } else if (isSpikeEntry(name)) {
                        hits += name
                    }
                }
            }
            return hits
        }

        val table = mutableListOf<String>()
        val failures = mutableListOf<String>()
        aars.sortedBy { it.name }.forEach { aar ->
            val hits = spikeHits(aar)
            val entryCount = java.util.zip.ZipFile(aar).use { zip -> zip.size() }
            val verdict = if (hits.isEmpty()) "CLEAN" else "SPIKE-LEAK"
            table += String.format("%-34s entries=%-4d spike_entries=%d %s", aar.name, entryCount, hits.size, verdict)
            if (hits.isNotEmpty()) failures += "${aar.name} -> ${hits.take(5)}"
        }
        logger.lifecycle("=== spike-absence audit (published release AARs) ===")
        table.forEach { logger.lifecycle(it) }
        if (failures.isNotEmpty()) {
            throw GradleException("spike/ code found in published AAR(s): ${failures.joinToString()}")
        }
    }
}

// --- Publishing to GitHub Packages (handoff §2) ----------------------------------------------
// Every module in `publishedModules` (defined above) publishes ONE `release` AAR publication under
// the single group `hk.edu.hkmu`, with `artifactId` equal to the module name and the version taken
// from the single `version` property in gradle.properties. `consumer-fixture` and `demo-app` are
// deliberately absent from `publishedModules` and are therefore never published.
//
// The logic lives here, applied by name across `publishedModules`, rather than in a `buildSrc` /
// convention plugin: this repo has neither, and a root-level block is the least invasive change that
// still keeps every module's publication in ONE place.
//
// Credentials are OPTIONAL at configuration time. The GitHub Packages repository reads them from
// Gradle properties (`github.actor` / `github.token`) or the environment (`GITHUB_ACTOR` /
// `GITHUB_TOKEN`) and leaves them null when absent, so configuring the build, running
// `publishToMavenLocal`, and every non-publishing task work with no secrets present. Only a real
// publish that contacts GitHub Packages requires credentials — none are ever hardcoded.
val gitHubPackagesUrl = "https://maven.pkg.github.com/Glass-Storm/ai-android"

subprojects {
    if (publishedModules.contains(name)) {
        pluginManager.withPlugin("com.android.library") {
            // Declare the publishable `release` AAR variant. AGP 8 requires opting in before a
            // `components["release"]` software component exists; sources jar included so the POM
            // points at real published sources.
            extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                    }
                }
            }

            apply(plugin = "maven-publish")

            // Publish the POM as the ONLY metadata contract. The generated Gradle Module Metadata
            // (`.module`) is derived from the raw dependency graph BEFORE `pom.withXml` strips the
            // vendored `flatDir` sherpa AAR, so it keeps `"group": null, "module":
            // "sherpa-onnx-1.13.8"` and is unparseable by a consumer
            // (`Expected a string but was NULL at $.variants[*].dependencies[*].group`). Disabling
            // the metadata task makes Gradle consumers fall back to the corrected POM.
            tasks.matching { it.name == "generateMetadataFileForReleasePublication" }
                .configureEach { enabled = false }

            // AGP registers the `release` component during its own afterEvaluate, so the
            // publication can only be created once that component exists.
            afterEvaluate {
                val releaseComponent = components["release"]
                // `project.name`, never the bare `name`: inside the publication lambdas below `name`
                // resolves to `MavenPublication.name` (the publication is literally called
                // "release"), which would set artifactId/name to "release".
                val moduleName = project.name
                extensions.configure<PublishingExtension>("publishing") {
                    publications {
                        create<MavenPublication>("release") {
                            from(releaseComponent)
                            // `group` is the single source of truth in gradle.properties; derive it
                            // from the project rather than repeating the literal here.
                            groupId = project.group.toString()
                            artifactId = moduleName
                            version = project.version.toString()
                            pom {
                                name.set("ai-android $moduleName")
                                description.set(
                                    "ai-android `$moduleName` module of the on-device speech and " +
                                        "sign-language library."
                                )
                                url.set("https://github.com/Glass-Storm/ai-android")
                            }
                            // Drop dependency entries without a real coordinate. The vendored
                            // sherpa-onnx AAR is a `flatDir` module dependency with no group/version
                            // and ships on no repository, so it can never be resolved from GitHub
                            // Packages; emitting an empty `<groupId>` would produce an invalid POM.
                            // (Consumers get that runtime via the pinned composite build — see
                            // docs/PUBLISHING.md — since it is a source/local artifact.)
                            pom.withXml {
                                val dependencies = (asNode().get("dependencies") as groovy.util.NodeList)
                                    .firstOrNull() as? groovy.util.Node ?: return@withXml
                                (dependencies.get("dependency") as groovy.util.NodeList)
                                    .toList()
                                    .forEach { node ->
                                        val dependency = node as groovy.util.Node
                                        val group = (dependency.get("groupId") as groovy.util.NodeList)
                                            .firstOrNull()?.let { (it as groovy.util.Node).text() }
                                        if (group.isNullOrBlank()) {
                                            dependencies.remove(dependency)
                                        }
                                    }
                            }
                        }
                    }
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            url = uri(gitHubPackagesUrl)
                            credentials {
                                username = providers.gradleProperty("github.actor").orNull
                                    ?: providers.environmentVariable("GITHUB_ACTOR").orNull
                                password = providers.gradleProperty("github.token").orNull
                                    ?: providers.environmentVariable("GITHUB_TOKEN").orNull
                            }
                        }
                    }
                }
            }
        }
    }
}

