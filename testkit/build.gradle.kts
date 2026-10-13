// Publishable test toolkit (fakes, fixtures, in-test helpers) consumed by the other modules.
// The unpublished consumer fixture APP is added separately in todo 12.
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
    namespace = "hk.edu.hkmu.testkit"
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
    // The sign module exposes the synthetic-landmark fixtures (`hk.edu.hkmu.testkit.sign`) that a
    // headless host CI feeds to the public `SignPipeline.recognizeLandmarks` seam, so `:testkit`'s
    // public API references `:sign` types (`SignClassifier`, `SignModelSpec`, `SignPipeline`, ...).
    // It is declared `implementation`, matching the existing `:core` pattern. The dependency is
    // one-way (`:testkit` -> `:sign`); `:sign` never depends on `:testkit` main, so there is no cycle.
    implementation(project(":sign"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
}
