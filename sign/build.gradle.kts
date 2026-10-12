plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.sign"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
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
    // Consumes the signed-manifest schema types (ModelFormat/Abi) so the landmarker `.task` is a
    // real manifest entry. The registry itself only needs `:core` (ModelRegistration/ModelLicense).
    implementation(project(":models"))
    implementation(libs.kotlinx.coroutines.core)

    // MediaPipe Tasks Vision = the landmark EXTRACTOR (Hand/Holistic), NOT the classifier. Android-only
    // JNI (libmediapipe_tasks_vision_jni.so) — host JVM cannot run it, so extraction is Tier B.
    implementation(libs.mediapipe.tasks.vision)

    // LiteRT is the SINGLE shipped sign-CLASSIFIER runtime, distinct from the MediaPipe extractor
    // above. The `litert` AAR carries libtensorflowlite_jni.so and depends transitively on
    // `litert-api` (org.tensorflow.lite.*), so this one coordinate binds the runtime + its Java API.
    // No second classifier runtime is added.
    implementation(libs.litert)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
}
