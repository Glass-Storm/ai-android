plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

dokka {
    dokkaPublications.html {
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        reportUndocumented.set(providers.gradleProperty("task31.reportUndocumented").isPresent)
    }
}

tasks.named("dokkaHtml") { actions.clear() }

android {
    namespace = "hk.edu.hkmu.diarize"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        // Tier B device conformance suite runs as real instrumentation tests (mirrors :asr).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    // Vendored sherpa-onnx AAR (NOT on Maven Central; see settings.gradle.kts flatDir(dir=libs/)).
    // sherpa-onnx ALREADY ships the offline speaker-diarization + speaker-embedding classes, so this
    // module adds NO new native runtime: it reuses libsherpa-onnx-jni.so + libonnxruntime.so that the
    // AAR bundles. Resolved as a flatDir MODULE dep (not `files(...)`) so AGP can assemble the AAR.
    implementation(mapOf("name" to "sherpa-onnx-${libs.versions.sherpaOnnx.get()}", "ext" to "aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)

    // Tier B device conformance (mirrors :asr). testkit provides the shared 16 KB/APK helpers.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(project(":testkit"))
}
