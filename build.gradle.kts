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
    // sherpa-onnx ships on no public repository as a stable artifact: vendored AAR.
    add(
        engineResolution.name,
        files("libs/sherpa-onnx-${libs.versions.sherpaOnnx.get()}.aar"),
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
