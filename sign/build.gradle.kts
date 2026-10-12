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
    namespace = "hk.edu.hkmu.sign"
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

    // Tier B device conformance (todo 28). testkit provides the shared 16 KB/APK helpers.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(project(":testkit"))
}
