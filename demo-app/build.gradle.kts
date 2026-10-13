// Demo application for the ai-android library.
//
// It exercises the library's REAL engines end to end: SenseVoice ASR, sherpa-onnx VITS TTS, the
// MediaPipe + LiteRT sign pipeline, llama.cpp recombination and the NLLB translation engine, plus
// the real licence gate, signed manifest and OpenCC converter. Large model weights download on
// demand (the "Models" section); the small artifacts ship inside the APK. The engines are arm64-only
// natives, so the APK targets arm64-v8a and the demo is meant for a physical arm64 phone. It is a
// demo, not a production sample: the UI is intentionally plain.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "hk.edu.hkmu.demo"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"

        // The engines are arm64-only (sherpa-onnx libonnxruntime.so, MediaPipe Tasks Vision,
        // llama.cpp / ggml natives). Ship a single ABI so the APK stays small and jniLibs merge
        // cannot pick up an unrelated test ABI.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":asr"))
    implementation(project(":tts"))
    implementation(project(":sign"))
    implementation(project(":recombine"))
    implementation(project(":models"))
    // :translate also pulls onnxruntime-android, whose libonnxruntime.so would collide with the one
    // sherpa-onnx's AAR already bundles (duplicate .so => mergeNativeLibs failure). Exclude it. The
    // translation demo still calls the real engine and is expected to fail for a different reason:
    // the NLLB SentencePiece tokenizer has no Android native.
    implementation(project(":translate")) {
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
    }
    implementation(libs.kotlinx.coroutines.android)
}
