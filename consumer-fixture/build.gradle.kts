// UNPUBLISHED consumer fixture. This is NOT a sample app and is NOT part of the published AAR set.
// It exists solely to prove the library compiles and packages against its PUBLIC API from a
// minified (`minifyEnabled`) release consumer. It is never shipped or documented as usage.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.fixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "hk.edu.hkmu.fixture"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"
    }

    buildTypes {
        release {
            // Minified on purpose: todo 30 inspects the minified fixture APK for retained JNI
            // bindings. Do not disable shrinking here.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            // UNPUBLISHED fixture: it inspects DEX only and never runs a native lib. Each engine module
            // ships exactly ONE ONNX Runtime source (sherpa's bundled libonnxruntime.so in :asr/:tts vs
            // onnxruntime-android's in :translate) — aggregating them here would otherwise collide in
            // mergeReleaseNativeLibs. Strip every native lib so the fixture stays a pure DEX/R8 probe.
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
    // The public API and the test-kit fakes are the ONLY library surface this fixture may touch.
    // `core` is declared directly (not transitively through testkit) so this module is a genuine
    // API-boundary check.
    implementation(project(":core"))
    implementation(project(":testkit"))
    implementation(libs.kotlinx.coroutines.core)

    // Engine modules are brought in ONLY so their shipped consumer ProGuard rules (proguard.txt in
    // each AAR) are applied to this minified consumer. The fixture NEVER references a binding class:
    // it stays on the public API, per todo 12. Todo 30 greps the minified APK to prove the consumer
    // rules retain the named JNI-bound classes/methods even though nothing here uses them.
    implementation(project(":asr"))
    implementation(project(":tts"))
    implementation(project(":translate"))
    implementation(project(":sign"))
    implementation(project(":recombine"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
