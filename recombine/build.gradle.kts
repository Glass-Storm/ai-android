// Recombination module. Its llama.cpp JNI binding is NOT a resolvable Maven artifact: it is
// built locally from a pinned source tree with CMake/NDK (Wave-0 S3 recipe). The Maven/Gradle
// dependency surface stays engine-free — only `:core` contracts plus coroutines.
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
    namespace = "hk.edu.hkmu.recombine"
    compileSdk = 36
    // Pinned NDK (Wave-0 S3 used 27.0.12077973 on this host).
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 28
        if (!task30DisableConsumerRules) {
            consumerProguardFiles("consumer-rules.pro")
        }

        ndk {
            // Production ABI is arm64-v8a ONLY. Test-only ABIs (x86_64) are added for the debug
            // variant below and must never reach the shipped release AAR.
            abiFilters.clear()
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // 16KB ELF page alignment (Android 15+ devices). Default NDK 27 emits 4KB.
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                // x86_64 is TEST-ONLY (emulator instrumented tests). The release AAR stays arm64-only.
                abiFilters += "x86_64"
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
