// Recombination module. Its llama.cpp JNI binding is NOT a resolvable Maven artifact: it is
// built locally from a pinned source tree with CMake/NDK (Wave-0 S3 recipe). The Maven/Gradle
// dependency surface stays engine-free — only `:core` contracts plus coroutines.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.recombine"
    compileSdk = 36
    // Pinned NDK (Wave-0 S3 used 27.0.12077973 on this host).
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 28

        ndk {
            // Production ABI is arm64-v8a; x86_64 is test-only.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // 16KB ELF page alignment (Android 15+ devices). Default NDK 27 emits 4KB.
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
}
