pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    // All repositories are declared here; project-level repositories are forbidden.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx (and potential engine fallbacks) only publish via JitPack.
        maven("https://jitpack.io")
        // Vendored AARs that ship on no repository (e.g. a locally-built binding).
        flatDir { dirs(rootDir.resolve("libs")) }
    }
}

rootProject.name = "ai-android"

// Production library modules. The wave-0 `spike/` project is a SEPARATE Gradle build
// (spike/settings.gradle.kts) and is intentionally NOT included here.
include(":core")
include(":asr")
include(":tts")
include(":diarize")
include(":translate")
include(":sign")
include(":recombine")
include(":models")
include(":testkit")
// Unpublished API-boundary fixture. It is NOT part of the published AAR set and is never shipped;
// it exists only to prove the library compiles against its public API from a minified consumer.
include(":consumer-fixture")
include(":demo-app")
