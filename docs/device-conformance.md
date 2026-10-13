# Device Conformance — ondevice-sign-asr-tts (Tier B)

> Tier B is **device-required**: every item is asserted on an arm64 device (or an x86_64 emulator)
> and auto-skips with `SKIPPED: no device` when none is attached. CI must never fail for a missing
> device. This is the living checklist; of the plan's Wave 5 todos, **todo 28** owns the full
> capability/device suite (real ASR/TTS/MT/SLR incl. MediaPipe extraction, sections 1–3 and 5–6) and
> **todo 29** owns the budget instrumentation recorded in section 4.

## 1. How to run

```bash
export JAVA_HOME=/path/to/jdk-21    # a JDK 21 toolchain
adb devices -l                       # must list at least one `device`
./gradlew connectedAndroidTest       # runs every instrumented suite
```

**No device attached?** The root Gradle guard (`build.gradle.kts`, "Tier B device-conformance guard")
probes `adb devices` before every `connected*AndroidTest` task:

- device present → the task runs normally;
- no device → the task is **skipped** (never a `DeviceException` failure), a `SKIPPED: no device`
  artifact is written to `build/tier-b/device-conformance-skipped.md`, and the build stays
  `BUILD SUCCESSFUL`.

This keeps CI green without hardware. Within a run, any assertion whose model is not provisioned
additionally auto-skips with an `Assume` reason (`SKIPPED: <reason>`), so a partially provisioned
device never produces a false failure. The device suites live in `<module>/src/androidTest/`:
`asr`, `tts`, `translate`, `sign` (each `*DeviceConformanceTest`).

## 2. Provisioning

Models are **never bundled** in the AAR. Push them to the device before running:

| Engine         | Device path                                                                | Required files                                                                                           | Source                                             |
| -------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| ASR            | `/data/local/tmp/sherpa-asr/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8/` | `model.int8.onnx`, `tokens.txt`                                                                          | sherpa-onnx SenseVoice int8 (Apache-2.0)           |
| ASR fixture    | `/data/local/tmp/sherpa-asr/test-wavs/`                                    | `yue-1.wav` (≤5 s, 16 kHz mono 16-bit PCM)                                                               | model `test_wavs/`                                 |
| TTS            | `/data/local/tmp/sherpa-tts/<directoryName>/`                              | per voice: `*.onnx`, `tokens.txt`, `lexicon.txt` (yue/zh), `rule.fst` (yue), `espeak-ng-data` (en)       | sherpa-onnx VITS/Piper                             |
| MT             | `/data/local/tmp/nllb/nllb-200-distilled-600M-int8/`                       | `encoder_model_int8.onnx`, `decoder_model_int8.onnx`, `sentencepiece.bpe.model`, `tokenizer_config.json` | `venddair/nllb-200-distilled-600M-onnx` (CC-BY-NC) |
| SLR landmarks  | `/data/local/tmp/sign/`                                                    | `holistic_landmarker.task`                                                                               | MediaPipe Tasks Vision (Apache-2.0)                |
| SLR classifier | `/data/local/tmp/sign/tgcn-wlasl-asl100/`                                  | `tgcn_asl100_float32.tflite`                                                                             | TGCN WLASL (non-commercial)                        |
| SLR fixture    | `/data/local/tmp/sign/frames/`                                             | ordered `*.png` of one signed letter/isolated sign                                                       | held-out clip                                      |

`directoryName`/`modelFile` values come from `TtsModelCatalog` / `SignModelCatalog` / `SignModelAssets`.

## 3. Device-only assertions (checklist)

Every row is an assertion that **cannot** be evaluated in CI. "Auto-skip" is the `Assume` reason when
its input is absent on an otherwise valid device.

### 3.1 Native packaging

- [ ] **N1 — 16 KB ELF page alignment of packaged natives.** Every `lib/<abi>/*.so` in the installed
      test APK has all `PT_LOAD` segments at `p_align == 0x4000`.
      Test: `<asr,tts,translate,sign>/*DeviceConformanceTest.packagedNativeLibrariesAre16KbAligned`
      (reuses `hk.edu.hkmu.core.native.ElfAlignmentVerifier`, the same parser as the todo-30 gate).
      Covers sherpa-onnx (`libonnxruntime.so`, `libsherpa-onnx-*.so`), ONNX Runtime
      (`libonnxruntime.so`, `libonnxruntime4j_jni.so`), MediaPipe
      (`libmediapipe_tasks_vision_jni.so`) and LiteRT (`libtensorflowlite_jni.so`).
      Expected: empty misaligned set. Auto-skip: no packaged natives.

### 3.2 ASR (sherpa-onnx SenseVoice)

- [ ] **A1 — real model load + transcription.** `transcribesProvisionedFixture` loads
      `SenseVoiceAsrEngine` from disk and transcribes `yue-1.wav`; asserts ≥1 segment with non-blank
      text.
- [ ] **A2 — final-segment contract.** Every emitted `TranscriptionSegment.isFinal == true` (offline
      model, no partials); a device run must not observe partials.
- [ ] **A3 — segment latency ≤ 1500 ms** for ≤5 s audio (asserted as wall-clock in `A1`).
- [ ] **A4 — peak PSS ≤ 400 MB** when ASR is enabled (section 4 / todo 29).
      Auto-skip for A1–A3: model or `yue-1.wav` not provisioned.

### 3.3 TTS (sherpa-onnx VITS/Piper)

- [ ] **T1 — real synthesis for every shipped voice** (Cantonese xiaomaiiwn, Mandarin VITS, English
      Piper). `SherpaTtsDeviceConformanceTest.synthesizesEveryShippedVoiceWithinRealTimeBudget`
      asserts the audio is non-empty and the **model-reported** rate is positive.
- [ ] **T2 — real-time factor ≤ 0.5** (synthesis wall-clock / audio duration), asserted per voice.
- [ ] **T3 — peak PSS ≤ 250 MB** when TTS is enabled (section 4 / todo 29).
      Auto-skip for T1–T2: the voice's directory is not provisioned.

### 3.4 Translation (ONNX Runtime NLLB-600M INT8)

- [ ] **M1 — real translation for all four prompt directions** (`en↔zh`, `en↔yue`), asserted non-blank
      by `NllbDeviceConformanceTest.translatesAllDirectionsOrDocumentsAndroidTokenizerGap`.
- [ ] **M2 — translation latency ≤ 3000 ms** per sentence (recorded from the device run).
- [ ] **M3 — peak PSS ≤ 1600 MB** when NLLB is enabled, **≤ 2200 MB** worst case with the recombine LLM
      resident (section 4 / todo 29).
- [x] **M4 — RESOLVED.** The tokenizer is now a pure-Kotlin SentencePiece BPE (`SentencePieceBpe`), so
      `M1` runs on-device with no Android native. Verified on the x86_64 emulator: `en->zh`
      你好,你今天怎么样? · `zh->en`/`yue->en` Hello, how are you today? · `en->yue` 你好,你今日好嗎?.
      Auto-skip remains only when the model is not provisioned.

### 3.5 Sign (MediaPipe landmark extraction → LiteRT classifier)

- [ ] **S1 — real MediaPipe landmark extraction** from decoded frames via
      `holistic_landmarker.task` (the 55-keypoint contract).
- [ ] **S2 — real LiteRT classification end-to-end.** `SignDeviceConformanceTest` runs the full
      `SignPipeline` (`VideoFrame → landmarks → segment → normalize → classify`) and asserts ≥1 result
      with a non-blank label.
- [ ] **S3 — window classify ≤ 200 ms** (averaged over produced windows).
- [ ] **S4 — peak PSS ≤ 250 MB** (landmarker + classifier enabled) (section 4 / todo 29).
- [ ] **S5 — open verification gap.** The holistic 55-keypoint **order** (13 upper-body + 21 left hand + 21 right hand) is a documented, overridable default and is **not yet confirmed on-device**;
      the same applies to left/right handedness on a third-person (non-selfie) frame. A device run
      must confirm the order before trusting handed features.
      Auto-skip for S1–S3: `.task`, classifier `.tflite`, or `frames/*.png` not provisioned.

### 3.6 Cross-cutting budgets (host + device)

- [ ] **B1 — per-model download size** within manifest `sizeBytes ±5%`, **total default download
      ≤ 1900 MB** (ASR + TTS-yue + NLLB + recombine) (section 4 / todo 29).
- [ ] **B2 — cold model-load time** ASR ≤5 s, NLLB ≤10 s, LLM ≤5 s; **warm load ≤1 s** (todo 29).
- [ ] **B3 — recombination LLM** first token ≤2000 ms and **PSS ≤ 600 MB**, and it must **not be
      resident unless used** (section 4 lazy-residency table / todo 29).
- [ ] **B4 — consumer APK-size delta ≤ 30 MB per ABI** (host-only: todo-30 `assembleRelease`).
- [ ] **B5 — GPU/NPU are not emulator-verifiable.** On an emulator only the CPU path is exercised;
      accelerator selection falls back to CPU by design (`GracefulBackendSelector`).

## Budget instrumentation (todo 29)

Peak resident PSS per feature and the total default download size are asserted against the plan's
Budgets table (`.omo/plans/ondevice-sign-asr-tts.md`, section "Budgets"). The instrumentation is
`hk.edu.hkmu.core.budget`:

- `MemoryBudget` — the per-feature PSS ceilings (source of truth: the Budgets table).
- `DownloadBudget` / `DeclaredModelSize` — total-default-download audit (≤1900 MB) and the per-model
  `sizeBytes` ±5% tolerance.
- `PssProbe` / `PeakPssRecorder` / `SamplingPssMonitor` — PSS sampling and peak tracking.
- `BudgetLedger` / `BudgetReport` / `BudgetExceededException` — record, evaluate, and assert.

Tier A (`./gradlew :core:testDebugUnitTest`, host JVM) proves the budget arithmetic with declared
sizes and a fake probe. The Tier B harness below measures a **real** device (`Debug.getPss()` for the
memory peaks, on-disk artifact sizes for the download total) and is exercised by
`core/src/test/.../budget/BudgetDeviceTierBTest.kt` (auto-skips without a device). Lazy residency of
the recombination LLM is proven in `recombine/src/test/.../LazyRecombinationResidencyTest.kt` through
the shared `ModelRegistry` state.

### Budgets table (targets) and Tier B result

| Item                                                            | Budget                   | Tier B measurement |
| --------------------------------------------------------------- | ------------------------ | ------------------ |
| Peak resident PSS — ASR (enabled)                               | ≤ 400 MB                 | SKIPPED: no device |
| Peak resident PSS — TTS (enabled)                               | ≤ 250 MB                 | SKIPPED: no device |
| Peak resident PSS — Translation (NLLB enabled)                  | ≤ 1600 MB                | SKIPPED: no device |
| Peak resident PSS — Sign (landmarker + classifier)              | ≤ 250 MB                 | SKIPPED: no device |
| Peak resident PSS — Recombination LLM (loaded)                  | ≤ 600 MB                 | SKIPPED: no device |
| Peak resident PSS — worst case (translate + recombine resident) | ≤ 2200 MB                | SKIPPED: no device |
| Per-model download size                                         | manifest `sizeBytes` ±5% | SKIPPED: no device |
| Total default download (ASR + TTS-yue + NLLB + recombine)       | ≤ 1900 MB                | SKIPPED: no device |

### Lazy-recombination residency (Tier A, host)

| Observable                                       | Expected | Observed   |
| ------------------------------------------------ | -------- | ---------- |
| Recombine disabled ⇒ resident recombination GGUF | 0        | 0 (Tier A) |
| Recombine enabled, before first `process()`      | 0        | 0 (Tier A) |
| Recombine enabled, after first `process()`       | 1        | 1 (Tier A) |
| Enabled minus disabled resident GGUF             | 1        | 1 (Tier A) |
| After `unload()`                                 | 0        | 0 (Tier A) |

> A budget figure may be **tightened** by a spike; loosening one requires a written, justified
> revision recorded in `.omo/evidence/`. No figure above has been revised.

## 5. Documented risks carried into Tier B

- **NLLB Android tokenizer — RESOLVED.** Replaced the `ai.djl.sentencepiece` JNI path with a
  pure-Kotlin SentencePiece BPE (no native). `M1` now runs on-device; see `SentencePieceBpe`.
- **Sign model provisioning.** The TGCN classifier is WLASL-derived (non-commercial) and the
  landmarker `.task` is ABI-independent — both must be pushed by the host; nothing ships in-tree.
- **MediaPipe 55-keypoint order / handedness.** Still unverified (see S5).
- **`libomp.so`.** `libggml-cpu.so` (recombine) `DT_NEEDED` `libomp.so`; the todo-30 packaging check
  must confirm it is present in the final APK/AAR or the recombine native fails to load on device.

## 6. Evidence when skipped

When no device is attached the guard writes `build/tier-b/device-conformance-skipped.md`
(task, reason, remedy) and the run still exits `BUILD SUCCESSFUL`. `adb devices -l`, the Gradle log,
and the artifact together form the `SKIPPED: no device` evidence recorded under
`.omo/evidence/task-28-ondevice-sign-asr-tts.{log,md}`.
