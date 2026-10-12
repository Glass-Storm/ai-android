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
    namespace = "hk.edu.hkmu.asr"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        // Tier B device conformance suite (todo 28) runs as real instrumentation tests.
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

    // Vendored sherpa-onnx AAR (NOT on Maven Central; see settings.gradle.kts flatDir(dir=libs/) + S4 decision P1).
    // It already bundles libonnxruntime.so, so onnxruntime-android must NOT be added alongside it.
    // Resolved as a flatDir MODULE dep (not `files(...)`): AGP refuses to build an AAR whose direct
    // dependency is a local .aar file (`hasLocalAarDeps`), which breaks `:asr:assembleRelease`.
    implementation(mapOf("name" to "sherpa-onnx-${libs.versions.sherpaOnnx.get()}", "ext" to "aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)

    // Tier B device conformance (todo 28). testkit provides the shared 16 KB/APK helpers.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(project(":testkit"))
}
