plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(files(rootProject.file("libs/sherpa-onnx-1.13.8.aar")))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
