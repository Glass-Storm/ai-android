# ai-android live demo

This document is the explainer and narration for the live demo. It covers what the project is,
what the demo app shows, what is real versus simulated, what cannot be shown today, the exact
commands to run on Windows, what to say while it runs, and the known limitations.

Run scripts here; do not edit any build or source file during the demo.

## What this is

`ai-android` is an on-device Android speech and sign-language library. It puts speech-to-text (ASR),
text-to-speech (TTS), text translation, sign-language reading, and an optional llama.cpp
recombination stage behind a small, framework-agnostic Kotlin API. Everything runs locally: no
cloud calls, no bundled models, and no camera or microphone capture owned by the library. A host
app supplies its own capture, model provisioning, and threading; the library supplies the engine
contracts, the lifecycle, the licence gate, and the composed pipelines.

## The demo app (`:demo-app`)

`:demo-app` is a simple Android demo app that exercises the public API. Package and applicationId
are `hk.edu.hkmu.demo`, and the launcher is `hk.edu.hkmu.demo.MainActivity`. A minimal UI drives six
buttons / scenarios:

1. Licence gate: show SelfUse versus Commercial intent against a NonCommercial model, and the
   bring-your-own path that always lands on `UnknownLicense` and is blocked for Commercial.
2. Signed-manifest tamper rejection: mutate a manifest byte and show it fails with
   `ManifestSignatureInvalid` before any descriptor is built.
3. Speech pipeline: `PipelineSpec.SpeechToText` (ASR to recombine to translate) and the observed
   `sourceText`, `recombinedText`, and `translatedText` per result.
4. Sign pipeline: `PipelineSpec.SignToText` (sign to recombine) through the frame-to-text facade.
5. Real 繁 to 簡 (Traditional to Simplified) OpenCC conversion, running in the app.
6. Cold-flow check: same stages in a different order is a different pipeline, and an illegal spec
   fails fast at construction with `PipelineMisconfigured`.

The app is a harness, not a product. It exists to show the contracts and the composition, on a
device, with a real UI in front of them.

## What is REAL vs SIMULATED

This part is deliberately blunt, so nobody walks away with the wrong impression.

### REAL

- The public API and its contracts: the `fun interface` engines, the value types, and the sealed
  `AiAndroidException` error taxonomy.
- Pipeline composition: `SpeechToTextPipeline` and `SignToTextPipeline`, the ordered
  `PipelineSpec`, `effectiveStages`, and fail-fast validation at construction.
- Streaming, cancellation, and backpressure semantics of the flows (cold, ordered, no delivery
  after cancellation).
- The licence gate: `DefaultLicensePolicy` with SelfUse versus Commercial; bring-your-own
  registration pins the licence to `UnknownLicense` and is blocked for Commercial. The gate runs at
  the shared load and download paths, not just inside the policy object.
- ECDSA signed-manifest verification: detached signature over the raw payload bytes against pinned
  vendor keys, before any payload decoding.
- The model-store download pipeline: licence gate, installed-artifact short-circuit, network policy,
  disk preflight, transfer in a `.part` file, sha256 verification, atomic rename; plus resume across
  process restarts, mirror failover on 5xx / timeout / truncated body, and concurrent-request
  dedup.
- OpenCC Traditional to Simplified and Simplified to Traditional conversion.
- The whole JVM test suite: 386 tests, 0 failures across 9 modules, on the host JVM.

### SIMULATED

- The ASR, TTS, translation, sign, and LLM engines wired into the demo app are the library's
  test-kit fakes. They have deterministic outputs so the pipelines and UI behave the same on every
  run.
- The real engines are Android-native and need multi-GB models that do not ship with the library.
- MediaPipe sign extraction needs an arm64 device; the fakes remove that dependency for the demo.

## What CANNOT be shown today

- Real speech recognition and real speech synthesis. The engines require large on-device models.
- Real translation. It is currently blocked: the NLLB SentencePiece tokenizer has no Android native
  build, and the desktop-JVM ONNX Runtime natives are Android-only.
- Real sign extraction on an x86_64 emulator. MediaPipe `tasks-vision` ships arm64-v8a only, so an
  arm64 device is required.
- Real LLM generation through the recombination stage.
- On-device latency and memory budgets. Those need the real engines and real models on real
  hardware.

## Prerequisites and exact commands (Windows)

Required:

- JDK 17 or 21. JDK 25 will break the build: Gradle 8.13 and AGP 8.13.0 are incompatible with it.
  The runners print `java -version` and refuse JDK 24 or newer with a clear message.
- Android SDK with `platform-36` (compileSdk 36) and matching build tools, plus `platform-tools`
  for `adb`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`).
- NDK `27.0.12077973` only if you build the `:recombine` natives. The demo app does not need it.
- The vendored AAR `libs\sherpa-onnx-1.13.8.aar`. It is gitignored; the runners fetch it and verify
  the SHA-256 automatically.

Native Windows (PowerShell):

```powershell
# JDK 17 or 21 (do NOT use JDK 24+)
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot'
java -version

# Android SDK
$env:ANDROID_HOME = 'C:\Users\<you>\AppData\Local\Android\Sdk'

# Run the demo
cd C:\path\to\ai-android
powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1

# Pre-warmed machine only
powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1 -Offline
```

Git Bash or WSL (note the forward slashes):

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot"
export ANDROID_HOME="/c/Users/<you>/AppData/Local/Android/Sdk"
cd /c/path/to/ai-android
bash scripts/demo.sh            # or: ./scripts/demo.sh
bash scripts/demo.sh --offline  # pre-warmed machine only
```

Or run it from Android Studio: open the project, wait for Gradle sync, select the `:demo-app` run
configuration, start an emulator or plug in a phone, then click Run.

The runners execute, in order: JDK check, AAR fetch and verify, then
`testDebugUnitTest ktlintCheck detekt verifyEngineResolution`, then `:demo-app:assembleDebug`, then
`adb install -r` and `adb shell am start -n hk.edu.hkmu.demo/.MainActivity` when a device is
attached. With no device, they print the Android Studio instructions instead.

## What to say (narration)

1. "This is a library, not an app. `:demo-app` is a thin harness that drives the public API so you
   can see it on a screen."
2. "Watch the headline number: 386 tests, 0 failures across 9 modules, on the host JVM. That is the
   whole contract surface, pipelines, licence gate, signed manifests, model store, and OpenCC."
3. "The licence gate blocks a Commercial intent on a non-commercial model, and bring-your-own
   always pins to `UnknownLicense`, so you cannot relabel a model as permissive by supplying it
   yourself."
4. "The signed manifest rejects a tampered payload before a single descriptor is built. The licence
   only ever comes from a verified manifest, which is what makes the gate trustworthy."
5. "The speech pipeline is ASR, then recombine, then translate. Same stages in a different order is
   a different pipeline, and an illegal order fails at construction, not at runtime."
6. "These engines are the test-kit fakes, on purpose, so the demo is deterministic. The real engines
   are Android-native and need multi-GB models that the library does not ship."
7. "OpenCC conversion is real. 繁 to 簡 and back run here in the app, not in a fake."
8. "The model store download path is real too: licence gate, sha256, resume, mirror failover, and
   dedup, in that order."

## Honest known limitations

From an independent review, currently true and worth stating plainly:

- `SignConfig.windowSize` is currently ignored by the sign engine. The window comes from the model
  spec, not from the config value.
- TTS synthesizes the whole utterance before emitting, so backpressure does not throttle native
  synthesis for that engine.
- A single APK that combines `:asr` or `:tts` with `:translate` hits a duplicate
  `libonnxruntime.so` unless native libs are excluded. The demo app excludes them to avoid the
  clash.
