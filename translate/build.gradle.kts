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

// Scratch switch (todo 30 packaging verification ONLY): when `-Ptask30.disableConsumerRules` is set,
// the shipped consumer rules are dropped so the minified consumer APK can be shown to LOSE the named
// JNI bindings — proving the keep rules are non-vacuous. Never set for a real build.
val task30DisableConsumerRules = providers.gradleProperty("task30.disableConsumerRules").isPresent

android {
    namespace = "hk.edu.hkmu.translate"
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
    implementation(libs.opencc4j)

    // ONNX Runtime (NLLB encoder + decoder). Do NOT add this to a module that also packages the
    // vendored sherpa-onnx AAR — sherpa already bundles libonnxruntime.so (duplicate .so).
    implementation(libs.onnxruntime.android)
    // SentencePiece JNI for the NLLB tokenizer (host/JVM; no Android native — see task-15 notes).
    implementation(libs.djl.sentencepiece)

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
