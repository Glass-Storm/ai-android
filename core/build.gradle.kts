plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "hk.edu.hkmu.core"
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

// Version of the OPTIONAL Koin container integration (compileOnly; never a hard dependency).
val koinVersion = "4.0.1"

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // OPTIONAL Koin integration. `compileOnly` keeps Koin OFF the runtime classpath and out of the
    // published POM, so the library never requires a container: the `aiAndroidModule(...)` facade is
    // the only code that touches Koin, and a host that does not call it never loads it. A Koin host
    // adds `io.insert-koin:koin-core` itself. Hilt is never required. The coordinate is pinned inline
    // (not in the shared version catalog) so this optional integration stays contained to `:core`.
    compileOnly("io.insert-koin:koin-core:$koinVersion")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
    // testkit supplies the shared fakes the facade test wires through the public API.
    testImplementation(project(":testkit"))
    // Koin is present in tests so the optional module's definitions can actually be resolved.
    testImplementation("io.insert-koin:koin-core:$koinVersion")
}
