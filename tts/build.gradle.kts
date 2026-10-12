plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Scratch switch (todo 30 packaging verification ONLY): when `-Ptask30.disableConsumerRules` is set,
// the shipped consumer rules are dropped so the minified consumer APK can be shown to LOSE the named
// JNI bindings — proving the keep rules are non-vacuous. Never set for a real build.
val task30DisableConsumerRules = providers.gradleProperty("task30.disableConsumerRules").isPresent

android {
    namespace = "hk.edu.hkmu.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!task30DisableConsumerRules) {
            consumerProguardFiles("consumer-rules.pro")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)

    // sherpa-onnx 1.13.8 is NOT published to Maven Central and JitPack only builds it on demand,
    // so the AAR is VENDORED (S4 decision P1): libs/sherpa-onnx-1.13.8.aar,
    // sha256 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96.
    // The AAR already bundles libonnxruntime.so — never also add onnxruntime-android to a module
    // that packages sherpa-onnx (duplicate .so => mergeNativeLibs failure).
    // Resolved as a flatDir MODULE dep (not `files(...)`): AGP refuses to build an AAR whose direct
    // dependency is a local .aar file (`hasLocalAarDeps`), which breaks `:tts:assembleRelease`.
    implementation(mapOf("name" to "sherpa-onnx-1.13.8", "ext" to "aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    // Tier B device conformance (todo 28). testkit provides the shared 16 KB/APK helpers.
    androidTestImplementation(project(":testkit"))
}
