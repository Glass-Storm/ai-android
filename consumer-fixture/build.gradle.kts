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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
