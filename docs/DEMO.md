# ai-android live demo

Explainer and narration for the live demo: what the project is, what the demo app shows, how to run
it on Windows, what to say while it runs, and the honest limitations.

Run the scripts here; do not edit build or source files during the demo.

## What this is

`ai-android` is an on-device Android speech and sign-language library. It puts speech-to-text (ASR),
text-to-speech (TTS), text translation, sign-language reading, and an optional llama.cpp
recombination stage behind a small, framework-agnostic Kotlin API. Everything runs locally: no cloud
calls, no bundled models, and no camera or microphone capture owned by the library. A host app
supplies its own capture and threading; the library supplies the engine contracts, the lifecycle, the
licence gate, and the composed pipelines.

## The demo app (`:demo-app`)

`:demo-app` is a small Android app that drives the **real** library engines. Package and applicationId
are `hk.edu.hkmu.demo`, and the launcher is `hk.edu.hkmu.demo.MainActivity`. It is **arm64-v8a only**.

The UI has three button groups over one scrolling log.

**Models** — one button per model. Each button checks the local file and its SHA-256 and only
downloads when the file is missing or corrupt (progress is logged every ~5%):

| Model                                                   | Approx size | Delivered          |
| ------------------------------------------------------- | ----------- | ------------------ |
| SenseVoice ASR (`model.int8.onnx` + `tokens.txt`)       | ~237 MB     | download on demand |
| Cantonese VITS TTS (onnx + lexicon + rule.fst + tokens) | ~115 MB     | download on demand |
| Qwen2.5-0.5B-Instruct Q4_K_M (recombine)                | ~491 MB     | download on demand |
| MediaPipe `holistic_landmarker.task`                    | ~14 MB      | bundled in the APK |
| TGCN `tgcn_asl100_float32.tflite` sign classifier       | ~2.4 MB     | bundled in the APK |
| Cantonese demo WAV, ASL demo clip                       | small       | bundled in the APK |

**Scenarios** — each runs a real engine and prints its output:

1. **ASR**: transcribe the bundled Cantonese WAV with the real sherpa-onnx SenseVoice model.
2. **TTS**: synthesize Cantonese with the real VITS model and play it through `AudioTrack`.
3. **Sign (video)**: decode the bundled ASL clip, extract MediaPipe landmarks, classify with the
   TGCN model, and print the label and confidence.
4. **Recombine**: run the real llama.cpp small-LLM recombination on a gloss.
5. **Licence gate**: `DefaultLicensePolicy` with SelfUse versus Commercial, plus the bring-your-own
   path that always lands on `UnknownLicense`.
6. **Signed manifest**: a tampered manifest fails with `ManifestSignatureInvalid` before any
   descriptor is built.
7. **OpenCC**: real 繁 to 簡 and back conversion.

### Live camera gesture example (arm64 + camera permission)

A third group, **Live camera**, opens `CameraGestureActivity` — a separate screen that mirrors the
mp4 gesture demo but sources frames from the **camera** instead of a video file. It is the live
counterpart of scenario 3 (Sign (video)) — the MediaPipe **per-image** gesture path — not the
temporal `SignPipeline`.

- **Pipeline** — CameraX `ImageAnalysis` (driven through `LifecycleCameraController`) → each analysis
  frame is encoded to a **JPEG still** (`ImageProxy` YUV_420_888 → NV21 → `YuvImage.compressToJpeg`)
  → `BitmapFactory.decodeByteArray` → `MediaPipeGestureRecognizer.recognize(bitmap)`. The recognised
  label and confidence update live under the `PreviewView`.
- **Backpressure (the §3 recipe)** — the analyser runs on its own executor and hands each JPEG to a
  **bounded** channel (capacity 1) with `trySendBlocking`, so a slow recognizer throttles the camera;
  CameraX then drops superseded frames instead of queuing them unbounded. Every `ImageProxy` is
  closed in the same pass regardless of the outcome.
- **Preconditions** — an **arm64 device** (MediaPipe Tasks Vision native is `arm64-v8a` only; the
  x86_64 emulator cannot run it) and the **camera permission**, requested on first open. The camera
  `<uses-feature>` is `required="false"`, so the app still installs on a camera-less emulator, where
  this screen simply reports that it cannot run.
- **How it differs from the mp4 demo** — the mp4 gesture demo decodes the bundled `demo_sign.mp4`
  frame by frame with no camera and no throttling; the live screen runs the same per-image
  recognizer against real camera frames and therefore must apply the bounded-channel recipe to stay
  bounded. Both call the identical `MediaPipeGestureRecognizer.recognize(bitmap)` entry point and
  load the same bundled `gesture/gesture_recognizer.task` bundle; neither uses the TGCN/`SignPipeline`
  classifier.

## What is REAL

Everything the app runs is the real library code:

- The **engines are the real engines**: sherpa-onnx ASR and TTS, ONNX Runtime, MediaPipe + LiteRT
  sign, and llama.cpp recombination. No test doubles.
- The public API and its contracts: the `fun interface` engines, the value types, and the sealed
  `AiAndroidException` error taxonomy.
- Pipeline composition, streaming, cancellation, and backpressure semantics of the flows.
- The licence gate at the shared load and download paths; ECDSA signed-manifest verification; the
  model-store download pipeline (licence gate, sha256, resume, mirror failover, dedup).
- The whole JVM test suite: 386 tests, 0 failures across 9 modules, on the host JVM.

## What to know before you claim it (honest caveats)

- **Sign is isolated signs, not letters or sentences.** The library's `SignPipeline` deliberately
  rejects the fingerspelling `[1, 30, 63]` layout (`ClassifierInputMapping.kt`), so the only video
  path is the TGCN **isolated-word** classifier. The demo prints one gloss per window; it is not a
  translation. The bundled clip is a real ASL sign, not the alphabet.
- **Translation works on Android.** The NLLB tokenizer is now a pure-Kotlin SentencePiece BPE (no
  native library at all). It is verified on-device in all four prompt directions (`en<->zh`, `en<->yue`).
- **First run downloads ~0.9 GB.** Nothing large ships in the APK beyond the landmarker, the sign
  classifier, and the demo media.

## Prerequisites and exact commands (Windows)

Required:

- JDK 17 or 21. JDK 25 will break the build: Gradle 8.13 and AGP 8.13.0 are incompatible with it.
  The runners print `java -version` and refuse JDK 24 or newer with a clear message.
- Android SDK with `platform-36` (compileSdk 36) and matching build tools, plus `platform-tools`
  for `adb`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`).
- NDK `27.0.12077973` only if you build the `:recombine` natives from scratch. The prebuilt APK does
  not need it.
- The vendored AAR `libs\sherpa-onnx-1.13.8.aar`. It is gitignored; the runners fetch it and verify
  the SHA-256 automatically.
- An **arm64** Android 9+ device. The demo app ships `arm64-v8a` only.

Native Windows (PowerShell):

```powershell
# JDK 17 or 21 (do NOT use JDK 24+)
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot'
java -version

# Android SDK
$env:ANDROID_HOME = 'C:\Users\<you>\AppData\Local\Android\Sdk'

# Run the demo (builds, then installs/launches on an attached arm64 device)
cd C:\path\to\ai-android
powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1
```

Git Bash or WSL (note the forward slashes):

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot"
export ANDROID_HOME="/c/Users/<you>/AppData/Local/Android/Sdk"
cd /c/path/to/ai-android
bash scripts/demo.sh            # or: ./scripts/demo.sh
```

Or run it from Android Studio: open the project, wait for Gradle sync, select the `:demo-app` run
configuration, attach an **arm64** phone, then click Run.

If you cloned the repo (rather than using the released APK), the bundled media is gitignored. Fetch
it from the `v0.0.1-alpha.1` release asset `demo-assets.zip` and unzip it into
`demo-app\src\main\assets\` before building.

## What to say (narration)

1. "This is a library, not an app. `:demo-app` is a thin harness that drives the real public API so
   you can see it on a screen."
2. "Everything here is the real code path: real sherpa-onnx ASR, real VITS TTS, real MediaPipe plus
   TGCN sign, and real llama.cpp recombination."
3. "The Models screen checks each file against a pinned SHA-256 and only downloads what is missing.
   Nothing large ships in the APK."
4. "Watch the speech demo: real speech recognition on a real audio clip, on-device, no network."
5. "The licence gate blocks a Commercial intent on a non-commercial model, and bring-your-own always
   pins to `UnknownLicense`."
6. "The signed manifest rejects a tampered payload before a single descriptor is built."
7. "For sign, be precise: this is isolated-sign classification on video, not fingerspelling and not
   sentence translation. That ceiling is in the library by design."
8. "The headline number is the suite: 386 tests, 0 failures across 9 modules, on the host JVM."

## Honest known limitations

From an independent review, currently true and worth stating plainly:

- The sign video path is isolated-word only. `SignConfig.windowSize` is also ignored by the sign
  engine; the window comes from the model spec.
- TTS synthesizes the whole utterance before emitting, so backpressure does not throttle native
  synthesis for that engine.
- On the x86_64 emulator, ASR/TTS latency and real-time factor exceed the device budgets (software path); a real arm64 phone is within budget.
- A single APK that combines `:asr` or `:tts` with `:translate` hits a duplicate `libonnxruntime.so`
  unless ONNX Runtime is excluded. The demo app excludes it.
