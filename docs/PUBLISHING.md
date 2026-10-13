# Publishing

This document covers how the library is published and how a consumer wires it in. It implements the
production deliverable **§2** of `docs/handoff/ai-android.1.md`: publish the library to **GitHub
Packages** with `maven-publish`, and document the **pinned composite-build** path for the interim.

## Coordinates

Every published module ships under the single group `hk.edu.hkmu`, with the **artifactId equal to the
module name** and the **version shared across the whole build**:

```
hk.edu.hkmu:<module>:<version>       # e.g. hk.edu.hkmu:core:0.0.1-alpha.1
```

| Module              | Publish? | Coordinate                         |
| ------------------- | -------- | ---------------------------------- |
| `:core`             | yes      | `hk.edu.hkmu:core:<version>`       |
| `:asr`              | yes      | `hk.edu.hkmu:asr:<version>`        |
| `:tts`              | yes      | `hk.edu.hkmu:tts:<version>`        |
| `:diarize`          | yes      | `hk.edu.hkmu:diarize:<version>`    |
| `:translate`        | yes      | `hk.edu.hkmu:translate:<version>`  |
| `:sign`             | yes      | `hk.edu.hkmu:sign:<version>`       |
| `:recombine`        | yes      | `hk.edu.hkmu:recombine:<version>`  |
| `:models`           | yes      | `hk.edu.hkmu:models:<version>`     |
| `:testkit`          | yes      | `hk.edu.hkmu:testkit:<version>`    |
| `:consumer-fixture` | **no**   | build-time API-boundary check only |
| `:demo-app`         | **no**   | sample app, never shipped          |

Only the modules listed in `publishedModules` in the root `build.gradle.kts` produce publications.
`consumer-fixture` and `demo-app` are deliberately excluded.

### Where the version (and group) live

There is exactly **one** place:

```properties
# gradle.properties
group=hk.edu.hkmu
version=0.0.1-alpha.1
```

Setting `group` here is what makes the inter-module dependencies (`implementation(project(":core"))`,
etc.) map to real `hk.edu.hkmu:core:<version>` coordinates inside the generated POMs. The publication
config in the root `build.gradle.kts` reads both `project.group` (as the publication `groupId`) and
`project.version`; nothing else hardcodes a group or a version. The Gradle version is semver and must
match the release tag used for the interim fix-up below (`v0.0.1-alpha.1`).

## How the publications are built

The root `build.gradle.kts` applies `maven-publish` to each `publishedModules` project and declares a
single `release` AAR variant:

```kotlin
android { publishing { singleVariant("release") { withSourcesJar() } } }
```

Each project then exposes one `MavenPublication` named `release`, built from the AGP `release` software
component:

- **packaging** `aar` (the release AAR, plus a `-sources.jar`),
- **groupId** `hk.edu.hkmu`, **artifactId** `<module>`, **version** `<version>`.

There is no `buildSrc` / `build-logic` in this repo, so the logic lives in one root-level block applied
by name rather than in a convention plugin — the least invasive change that still keeps every
publication in one place.

The **POM is the only metadata contract**. Gradle Module Metadata (`.module`) is derived from the raw
dependency graph _before_ the POM fix-up below runs, so for `:asr`, `:tts`, and `:diarize` it would
carry the vendored sherpa dependency with a null `group` and be unparseable by consumers. The
publication therefore disables the `generateMetadataFileForReleasePublication` task, so no `.module`
file is published and Gradle consumers fall back to the corrected POM.

### The vendored sherpa-onnx AAR and the POM

`:asr`, `:tts` and `:diarize` depend on the vendored `libs/sherpa-onnx-1.13.8.aar` via a `flatDir`
repository. That artifact has no group or version and ships on **no** repository, so it can never be
resolved from GitHub Packages. The publication therefore **drops dependency entries that have no real
coordinate** from the POM, rather than emitting an invalid empty `<groupId>`. A consumer that needs the
sherpa runtime uses the composite-build path below, where the local `flatDir` AAR is available.

## Publishing to GitHub Packages

Target repository:

```
https://maven.pkg.github.com/Glass-Storm/ai-android
```

Credentials are **never hardcoded**. They are read, in order, from a Gradle property or the
environment, and may be absent:

| Credential | Gradle property | Environment    |
| ---------- | --------------- | -------------- |
| username   | `github.actor`  | `GITHUB_ACTOR` |
| password   | `github.token`  | `GITHUB_TOKEN` |

Configuration, local publishing, and every non-publishing task work **without** credentials. Only a
real upload that contacts GitHub Packages needs them.

```bash
export JAVA_HOME=/path/to/jdk-21

# Dry run: publish into the local Maven cache (~/.m2/repository). No credentials needed.
./gradlew publishToMavenLocal

# Real publish to GitHub Packages. GITHUB_TOKEN needs `write:packages` for this repo.
GITHUB_ACTOR=<your-user> GITHUB_TOKEN=<token> ./gradlew publish
```

`publish` runs every module's `publishReleasePublicationToGitHubPackagesRepository`. Artifacts are
immutable by convention: never re-upload the same version. Bump the `version` in `gradle.properties`
and the tag together.

## Interim consumption: pinned composite build

Until the GitHub Packages flow is live, consume the library as a **composite build pinned to a fixed
tag** — never a moving branch. Check the repo out at the exact tag that matches the published version
(`v0.0.1-alpha.1`), then include it from the consumer's `settings.gradle.kts`. The `includeBuild(...)`
must sit at the **top level** of `settings.gradle.kts`, **outside** any `pluginManagement` block: a
`pluginManagement { includeBuild(...) }` only contributes **plugins** and does **not** substitute
library dependencies, so `hk.edu.hkmu:core` would fail to resolve.

```kotlin
// consumer settings.gradle.kts — top level, NOT inside pluginManagement
includeBuild("../ai-android")   // or a git URL, checked out at the pinned tag
```

For a remote checkout, pin the revision explicitly so it can never float. This one is a top-level
`sourceControl` block (not under `pluginManagement`), and it substitutes the library modules:

```kotlin
// consumer settings.gradle.kts — top level, NOT inside pluginManagement
sourceControl {
    gitRepository(uri("https://github.com/Glass-Storm/ai-android.git")) {
        producesModule("hk.edu.hkmu:core")
        producesModule("hk.edu.hkmu:sign")
        // ... one producesModule(...) per module actually consumed
    }
}
```

and check out the tag `v0.0.1-alpha.1` in the consumer's checkout step (a `git checkout --detach
v0.0.1-alpha.1`, a pinned submodule SHA, or the equivalent CI step). The rule is simple: **a tag, not a
branch**. The hub's `hub.1.md` decision is that it is never handed a moving AAR.

Consuming through the composite build has one advantage worth noting: the vendored `sherpa-onnx` AAR
resolves from this repo's `libs/` `flatDir` for `:asr`, `:tts`, and `:diarize`, which the GitHub
Packages POM cannot express.

### Verifying a composite-build consumer

```bash
# In the consumer project
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep hk.edu.hkmu
```

You should see the `hk.edu.hkmu:*` modules substituted by the included build.

## Local verification of this repo's publications

```bash
export JAVA_HOME=/path/to/jdk-21

./gradlew publishToMavenLocal                      # every publishedModules AAR + POM
./gradlew dokkaHtml                                # aggregated API docs
./gradlew ktlintCheck detekt                       # static analysis

# Inspect a generated POM
cat ~/.m2/repository/hk/edu/hkmu/core/0.0.1-alpha.1/core-0.0.1-alpha.1.pom
```

The POM must show `groupId=hk.edu.hkmu`, `artifactId=core`, `version=0.0.1-alpha.1`,
`packaging=aar`.
