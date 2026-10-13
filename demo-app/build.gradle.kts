// Demo application for the ai-android library.
//
// It exercises the library's PUBLIC API end to end with test-kit fakes standing in for the engines,
// so it needs no bundled model, no native runtime and no network. It is a demo, not a production
// sample: the UI is intentionally plain.
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // :asr/:tts bundle sherpa's ONNX Runtime while :translate bundles ORT 1.22, so merging
            // their native libs would collide. The demo drives test-kit fakes and never loads a
            // native library, so every .so is dropped here.
            excludes += "**/*.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":testkit"))
    implementation(project(":models"))
    implementation(project(":translate"))
    implementation(project(":asr"))
    implementation(project(":tts"))
    implementation(project(":sign"))
    implementation(project(":recombine"))
    implementation(libs.kotlinx.coroutines.android)
}
