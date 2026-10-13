# ai-android

On-device speech and sign-language library for Android. It bundles speech-to-text, text-to-speech,
text translation, sign-language reading, and an optional small-LLM recombination stage behind a small,
framework-agnostic Kotlin API. Everything runs locally: no cloud calls, no bundled models, no camera
or microphone capture owned by the library.

This is a **library only**. There is no service, no sample app, and no turnkey UI. A host app supplies
its own capture, model provisioning, and threading; the library supplies the engine contracts, the
lifecycle, the license gate, and the composed pipelines.

## Modules

| Module              | Ship to consumers | Responsibility                                                                                                                            |
| ------------------- | ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `:core`             | yes               | Engine contracts, configuration, DI facade, license policy interface, error taxonomy, model registry, native loader, pipeline composition |
| `:asr`              | yes               | Streaming speech-to-text engine (sherpa-onnx SenseVoice)                                                                                  |
| `:tts`              | yes               | Streaming text-to-speech engine (sherpa-onnx VITS)                                                                                        |
| `:diarize`          | yes               | Offline local speaker diarization + voiceprint enrollment (sherpa-onnx pyannote segmentation + speaker embedding)                         |
| `:translate`        | yes               | Text translation engine (NLLB-200 INT8 on ONNX Runtime) plus OpenCC script conversion                                                     |
| `:sign`             | yes               | Sign-language landmark extraction, segmentation, classification, and the frame-to-text facade                                             |
| `:recombine`        | yes               | Small-LLM recombination post-processor (llama.cpp)                                                                                        |
| `:models`           | yes               | Signed-manifest loading, model downloader, offline import, bring-your-own registration, upgrade and rollback                              |
| `:testkit`          | yes               | Shared fakes and fixtures for host tests                                                                                                  |
| `:consumer-fixture` | **no**            | Build-time API-boundary check only. It compiles the public API against a minified consumer. It is not a sample app and is never published |

Minimal requirement: `minSdk 28`, Kotlin 2.1, coroutines. The public API is framework-light. It exposes
`kotlinx.coroutines.flow.Flow` and plain data types, so it does not force a DI container, and it does not
require Hilt anywhere.

## Engine contracts

Every engine is a `fun interface`, so a host can supply a lambda in tests. Each method returns a cold
`Flow`, and nothing is loaded until the flow is collected.

```kotlin
fun interface AsrEngine {
    fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment>
}

fun interface DiarizationEngine {
    fun diarize(audio: Flow<AudioChunk>): Flow<DiarizationSegment>
}

fun interface SpeakerEmbeddingExtractor {
    fun extract(audio: Flow<AudioChunk>): Flow<Voiceprint>
}

fun interface TtsEngine {
    fun synthesize(text: String): Flow<AudioChunk>
}

fun interface TranslationEngine {
    fun translate(text: String): Flow<TranslatedText>
}

fun interface SignEngine {
    fun recognize(frames: Flow<SignFrame>): Flow<SignPrediction>
}

fun interface PostProcessor {
    fun process(text: String): Flow<String>
}
```

Supporting value types: `AudioChunk` (normalized Float32 PCM plus a sample rate), `TranscriptionSegment`,
`DiarizationSegment`, `Voiceprint`, `TranslatedText`, `SignFrame`, and `SignPrediction`. Language is the inline value class `Language`, with
the constants `Language.Auto`, `English`, `Cantonese`, `Mandarin`, `Japanese`, and `Korean`.

**Diarization runs locally and is optional.** `:diarize` reuses the already-bundled sherpa-onnx
runtime (no new native dependency) to attribute audio to speakers. A host runs `DiarizationEngine.diarize`
over the same audio it feeds to ASR and folds the labels onto the matching `TranscriptionSegment`
spans via the additive, default-`null` `speakerLabel`/`speakerId` fields.

```kotlin
val diarizer = SherpaDiarizationEngine.create(
    config = DiarizationConfig(),
    modelDir = File(context.filesDir, "diarization")
)
val turns = diarizer.diarize(audio).toList()

// Fold each speaker turn onto the ASR segments that start inside it. `speakerId` is 0-based;
// `speakerLabel` is its 1-based display form ("Speaker N"). Both are assigned per run, so do not
// treat them as stable across separate calls.
val attributed = asrSegments.map { segment ->
    val turn = turns.firstOrNull {
        it.startTimeMillis <= segment.startTimeMillis && segment.startTimeMillis < it.endTimeMillis
    }
    segment.copy(speakerLabel = turn?.speakerLabel, speakerId = turn?.speakerId)
}
```

**Speaker enrollment runs locally and is optional.** The same `:diarize` module ships a
`SpeakerEmbeddingExtractor` (also a `fun interface`) that turns one speaker's audio into a reusable
`Voiceprint`. It reuses the exact CAM++ embedding graph the diarizer bundles — no new native runtime,
no new dependency. Enroll each known speaker once, then resolve an incoming voiceprint to a display
name purely by cosine similarity against a threshold.

```kotlin
val extractor = SherpaSpeakerEmbeddingExtractor.create(
    config = SpeakerEmbeddingConfig(),
    modelDir = File(context.filesDir, "diarization")
)

// Enroll: extract a voiceprint for one speaker's isolated turn and bind it to a name.
val aliceVoiceprint = extractor.extract(aliceAudio).single()
val directory = EnrollmentDirectory(threshold = 0.6f).enroll("Alice", aliceVoiceprint)

// Resolve: map an incoming voiceprint to the closest enrolled speaker, or null below threshold.
val match: SpeakerMatch? = directory.match(extractor.extract(incomingAudio).single())
val name = match?.displayName ?: "Unknown"

extractor.close()
```

`EnrollmentDirectory` and `VoiceprintMatcher` are pure Kotlin: they hold no native handle, so a
directory can be built, persisted and matched entirely on the host JVM. The similarity / threshold /
match decision is a pure function (`VoiceprintMatcher.cosineSimilarity` and
`VoiceprintMatcher.bestMatch`). The extractor is license-gated like the other engines, loads its native
session lazily on the first non-empty audio, is CPU-only, and validates the model asset before any
native call.

## Streaming semantics

The flows are cold and ordered. Cancelling collection stops the work and releases the native session;
no element is delivered after cancellation.

**ASR emits final segments, never partials.** `AsrEngine.transcribe` returns exactly one
`TranscriptionSegment` per detected utterance, in chronological order, and every segment has
`isFinal == true`. The underlying recognizer is offline and whole-segment, so there is no interim
hypothesis to expose. The flow completes after the last segment.

**TTS emits PCM final chunks.** `TtsEngine.synthesize` streams contiguous `AudioChunk`s of normalized
Float32 PCM in `[-1.0, 1.0]` at the model's sample rate. Concatenating the chunks yields the complete
utterance. A monophonic `synthesizeInt16` convenience converts to 16-bit PCM at the boundary.

**Translation emits text, not tokens.** `TranslationEngine.translate` emits one `TranslatedText` per
input segment, in order, then completes.

**Diarization emits speaker turns, in order.** `DiarizationEngine.diarize` emits one
`DiarizationSegment` (start, end, `speakerLabel`) per detected speaker turn, in chronological order,
then completes; empty audio emits nothing. The license gate, asset check and native diarizer are all
created lazily at load, when the first non-empty audio reaches the flow.

**Speaker embedding emits one voiceprint per utterance.** `SpeakerEmbeddingExtractor.extract` emits
exactly one `Voiceprint` for the whole utterance, then completes; empty audio emits nothing. The
license gate, asset check and native extractor are created lazily at load, when the first non-empty
audio reaches the flow.

**Sign recognition emits at a window boundary.** `SignEngine.recognize` accumulates frames and emits one
`SignPrediction` once a full window of `windowSize` frames has been collected, advanced by `stride`.
It never emits per frame, and overlapping windows can describe overlapping time ranges.

**Recombination is the only token stream.** `PostProcessor.process` emits partial pieces as the small
LLM generates them. Concatenating the pieces yields the final text, so a consumer may render it
incrementally. When recombination is disabled, an implementation may emit the input unchanged as a
single piece.

**Backpressure.** Implementations bridge native callbacks with `callbackFlow` or `channelFlow` and
`trySendBlocking`, so a slow collector throttles the native producer rather than dropping an element.

## Configuration

Shared runtime knobs live in `EngineRuntimeConfig`, and each engine has its own config type:

```kotlin
data class EngineRuntimeConfig(
    val contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS, // 512, reload-costed
    val accelerator: Accelerator = Accelerator.Auto
)

data class AsrConfig(val runtime: EngineRuntimeConfig, val language: Language = Language.Auto, val sampleRateHz: Int = 16_000)
data class TtsConfig(val runtime: EngineRuntimeConfig, val language: Language, val sampleRateHz: Int, val speakerId: Int = 0)
data class DiarizationConfig(val runtime: EngineRuntimeConfig, val sampleRateHz: Int = 16_000, val numThreads: Int = 1, val numClusters: Int = AUTO_CLUSTERS, val clusteringThreshold: Float = 0.5f, val minDurationOnSeconds: Float = 0.2f, val minDurationOffSeconds: Float = 0.5f)
data class TranslationConfig(val runtime: EngineRuntimeConfig, val source: Language, val target: Language, val scriptConversion: Boolean = true)
data class SignConfig(val classifierId: String, val runtime: EngineRuntimeConfig, val windowSize: Int = 30, val stride: Int = 10, val language: Language)
data class PostProcessConfig(val recombinationEnabled: Boolean = true, val runtime: EngineRuntimeConfig, val language: Language, val maxInputChars: Int = 512)
```

`EngineRuntimeConfig.contextWindowTokens` is reload-costed: changing it invalidates any native context
sized for the previous window, so treat it as a load-time parameter.

`Accelerator` is `Auto`, `Cpu`, or `Gpu`. `Auto` selects a GPU backend and falls back to CPU, while an
explicit `Gpu` is strict and raises `AiAndroidException.AcceleratorUnavailable` when no GPU is present.

## Assembling engines

The library never instantiates engines itself. A host implements `EngineFactory` and hands engines to
the library through one of three equivalent entry points. All of them produce the same
`AiAndroidEntryPoint` contract.

```kotlin
interface EngineFactory {
    fun createAsrEngine(config: AsrConfig): AsrEngine
    fun createTtsEngine(config: TtsConfig): TtsEngine
    fun createTranslationEngine(config: TranslationConfig): TranslationEngine
    fun createSignEngine(config: SignConfig): SignEngine
    fun createPostProcessor(config: PostProcessConfig): PostProcessor
}
```

Assemble through a factory:

```kotlin
val graph = AiAndroidGraph.builder()
    .factory(myFactory)
    .asr(AsrConfig(EngineRuntimeConfig(), Language.Cantonese))
    .tts(TtsConfig(EngineRuntimeConfig(), Language.Cantonese, sampleRateHz = 22_050))
    .build()

val asr = graph.asr()
```

Or hand over engines you already own:

```kotlin
val graph = AiAndroidGraph.of(asr = asrEngine, tts = ttsEngine, sign = signEngine)
```

Or, if you use Koin, use the optional module. Koin stays out of the runtime classpath unless you call
it, so hosts that prefer manual wiring never pay for it. Hilt is supported the same way any custom
container is: implement `AiAndroidEntryPoint` directly.

A graph may be partial. Requesting an unassembled stage throws
`AiAndroidException.MissingDependency` naming the engine (for example `"AsrEngine"`), and `graph.has(slot)`
probes an optional stage without catching.

## Pipeline ordering

Composition lives in `:core` and is expressed as an ordered, validated `PipelineSpec`. The order is the
contract, so the same stages with a different order are a different pipeline.

```kotlin
PipelineSpec.SpeechToText // [Asr, Recombination, Translation]
PipelineSpec.SignToText   // [Sign, Recombination]
```

The default specs place recombination between the source stage and any later text stage. Recombination
is default-ON but lazy: it is invoked once per emitted segment and only when `recombinationEnabled` is
true, so an opt-out never touches the lazily loaded LLM.

```kotlin
val pipeline = SpeechToTextPipeline(
    spec = PipelineSpec.SpeechToText,
    asr = asrEngine,
    translation = translationEngine,
    postProcessor = postProcessor
)

pipeline.transcribe(audio).collect { result ->
    // result.sourceText -> result.recombinedText -> result.translatedText
}

val signPipeline = SignToTextPipeline(
    spec = PipelineSpec.SignToText,
    sign = signEngine,
    postProcessor = postProcessor
)
signPipeline.recognize(frames).collect { result -> println(result.text) }
```

`effectiveStages` reports exactly which stages run after the opt-out is applied, and each result carries
the intermediate `recombinedText` or `translatedText`, so a caller can observe what each stage produced.

An illegal spec fails fast with `AiAndroidException.PipelineMisconfigured`: an empty order, a duplicate
stage, a stage illegal for the kind (translation is not a sign stage), a post-processor before the
source, a spec of the wrong `PipelineKind`, or a declared stage with no engine. All of these are rejected
at construction, before any frame is consumed.

`hk.edu.hkmu.core.pipeline` is the generic, interface-level composition. `hk.edu.hkmu.sign.pipeline.SignPipeline`
in `:sign` is a concrete frame-to-text facade that owns landmark extraction, segmentation, and
classification for you.

## License policy

Every model carries a `ModelLicense` (`kind`, optional `spdxId`, optional `notice`). A `LicensePolicy`
decides whether that model may be used for a given `UsageIntent`.

```kotlin
enum class UsageIntent { SelfUse, Commercial }
enum class LicenseKind { Permissive, NonCommercial, UserProvided, UnknownLicense }

interface LicensePolicy {
    fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision
}

sealed interface LicenseDecision {
    data class Allowed(val acknowledgment: String? = null) : LicenseDecision
    data class Restricted(val reason: String, val license: ModelLicense, val intent: UsageIntent) : LicenseDecision
}
```

The shipped `DefaultLicensePolicy` behaves as follows:

- `SelfUse` allows `Permissive` unconditionally, allows `NonCommercial` with a recorded
  `acknowledgment`, and allows `UserProvided` with an acknowledgment.
- `Commercial` allows only `Permissive`. It blocks `NonCommercial`, `UserProvided`, and it **always**
  blocks `UnknownLicense`.

The gate runs at the shared load paths, not just inside the policy object. `ModelRegistry` evaluates the
policy before it instantiates a model, and `ModelStore` evaluates it before it writes a byte, so a
restricted model never loads and never downloads. The license always comes from a signature-verified
manifest, which is what stops a tampered manifest from relabelling a non-commercial model as permissive.

## Models: download, offline import, and bring-your-own

The `:models` module owns acquisition and integrity. No model ships in the library.

**Signed manifest.** `SignedManifestLoader.load(envelopeJson)` decodes an envelope, verifies a detached
ECDSA signature over the raw payload bytes against pinned vendor keys, and only then decodes and validates
the payload into `ModelDescriptor`s. A tampered or unsigned manifest fails with
`AiAndroidException.ManifestSignatureInvalid`, and no descriptor is ever constructed. Descriptors have an
internal constructor, so consumers cannot mint one.

**Download.** `ModelStore.download(descriptor, intent)` returns a cold `Flow<DownloadProgress>`. The order
is deliberate: license gate, then installed-artifact short-circuit, then network policy, then disk
preflight, then transfer, then sha256 verification, then an atomic rename onto the final path. Bytes land
in a `.part` file and a sidecar lets an interrupted transfer resume across process restarts. Mirrors fail
over on `5xx`, timeout, or a truncated body. Concurrent requests for the same model are deduplicated, so
two collectors trigger exactly one transfer. Cancelling the collector aborts the transfer and deletes the
partial.

**Offline import and side-loading.** `ModelStore.importPreProvisioned(descriptor, sourceFile, intent)`
verifies a pre-provisioned file against the pinned sha256 and installs it without any network use. It
emits a single terminal `DownloadProgress` with phase `Imported`.

**Bring your own (BYO).** `UserProvidedModelRegistrar.register(model)` turns a host-supplied
`UserProvidedModel` into a verified descriptor. There is no signature on this path by design, so the
license is pinned to `UnknownLicense` regardless of any declared license. A host cannot use BYO
registration to relabel a model as permissive, and cannot sign the vendor manifest (that needs a pinned
vendor key). This is the path used for a BYO Cantonese Sign Language classifier.

**Lifecycle.** `ModelRegistry` keeps loaded models resident and evictable, hands out pinned leases through
`acquire` (an `AutoCloseable` `ModelLease`), and frees idle models on `unload` or `onTrimMemory`. It never
evicts an in-use model.

**Upgrade and rollback.** `SignedManifestLoader` and the version helpers support upgrade and rollback. An
older version is retained until the new artifact passes verification, so a failed verification rolls back
to the still-active previous version.

## Sign-language capability ceiling

The sign pipeline recognizes fingerspelled letters and isolated signs. It is not a continuous
sign-language translator, and it does not generate sentences from signing.

sign reading outputs letters and isolated signs only and does not translate continuous signing sentences

Self-use builds reach fingerspelling and isolated ASL glosses. Commercial builds reach only the
commercially-licensed fingerspelling model, because the isolated-ASL classifier is derived from a
non-commercial dataset. Continuous Cantonese Sign Language is a bring-your-own model slot: register your
own classifier through `UserProvidedModelRegistrar`, and no CSL model ships with the library.

The recombination stage concatenates and cleans up recognized labels. It is token recombination, not
sentence generation, and it does not widen the ceiling.

## Errors

Every failure is a subclass of the sealed `AiAndroidException`, so callers catch a precise type instead
of matching message strings. The subclasses carry structured fields and include:
`LicenseRestricted`, `ManifestSignatureInvalid`, `ManifestInvalid`, `ModelAssetMissing`, `ModelLoadFailed`,
`ModelInUse`, `ModelNotAvailable`, `ChecksumMismatch`, `NativeLibraryMissing`, `UnsupportedLanguage`,
`InputTooLong`, `InputShapeMismatch`, `MalformedFrame`, `AcceleratorUnavailable`, `PipelineMisconfigured`,
`MissingDependency`, and `ScriptConversionError`.

## API reference

Generated API docs are produced with Dokka. Run:

```bash
./gradlew dokkaHtml
```

The aggregated HTML lands in `build/dokka/html/`, and `build/dokka/html/index.html` is the entry point.
`failOnWarning` is enabled, so a broken KDoc reference fails the build instead of shipping a dangling
link.

## Building and verifying

The `:asr`, `:tts`, and `:diarize` modules resolve the sherpa-onnx Android AAR from a local `libs/`
directory (`flatDir`). It is a binary and is **not** committed, so fetch it once before building:

```bash
./scripts/fetch-sherpa-aar.sh
```

```bash
export JAVA_HOME=/path/to/jdk-21    # a JDK 21 toolchain (AGP 8.13 requires JDK 17+)

./gradlew testDebugUnitTest ktlintCheck detekt   # unit tests and static analysis
./gradlew dokkaHtml                              # API documentation
./gradlew assembleRelease                        # release AARs
./gradlew verifyNativeAlignment                  # 16 KB ELF alignment gate
```

The production ABI is `arm64-v8a`. Test ABIs are never shipped, and every vendored native library is
checked for 16 KB page alignment because Android 15+ devices can require it.
