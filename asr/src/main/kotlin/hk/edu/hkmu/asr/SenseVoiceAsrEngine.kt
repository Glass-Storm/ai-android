package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [AsrEngine] backed by sherpa-onnx SenseVoice.
 *
 * ### Emission semantics (final segments, NOT token-level partials)
 * [transcribe] accepts **16 kHz mono** PCM (`AudioChunk.samples` normalized Float32 in `[-1, 1]`)
 * and emits exactly one `TranscriptionSegment` per detected utterance, in chronological order.
 * SenseVoice is an offline, whole-utterance model: there is no token-level or partial hypothesis to
 * expose, so **every emitted segment has `isFinal == true`**. The flow completes normally after the
 * last utterance; cancelling collection stops all further emissions.
 *
 * Segmentation uses an energy VAD ([AsrAudioSegmenter]) so a continuous microphone stream is cut
 * into utterances before each whole-utterance recognition.
 *
 * ### Lifecycle
 * The native recognizer is created lazily on first collection (never at construction, so the engine
 * can be built without loading ~234 MB of model weights) and released by [close]. Assets are
 * validated against [AsrModelAssets] before that first native call.
 */
class SenseVoiceAsrEngine internal constructor(
    private val config: AsrConfig,
    private val recognizerProvider: () -> SenseVoiceRecognizer,
    private val dispatcher: CoroutineDispatcher
) : AsrEngine, AutoCloseable {

    private val recognizerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { recognizerProvider() }
    private val recognizer: SenseVoiceRecognizer by recognizerDelegate

    init {
        // Fail fast on an unsupported language before any audio (or native) work happens.
        SenseVoiceLanguages.toModelCode(config.language)
    }

    override fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment> = flow {
        val segmenter = AsrAudioSegmenter(config.sampleRateHz)
        audio.collect { chunk ->
            requireSampleRate(chunk)
            segmenter.accept(chunk.samples).forEach { emitUtterance(it) }
        }
        segmenter.flush().forEach { emitUtterance(it) }
    }.flowOn(dispatcher)

    /** Releases the native recognizer if it was created. Idempotent. */
    override fun close() {
        if (recognizerDelegate.isInitialized()) {
            recognizer.close()
        }
    }

    private fun requireSampleRate(chunk: AudioChunk) {
        if (chunk.sampleRateHz != config.sampleRateHz) {
            throw AiAndroidException.MalformedFrame(
                "expected ${config.sampleRateHz} Hz mono PCM, got ${chunk.sampleRateHz} Hz"
            )
        }
    }

    private suspend fun FlowCollector<TranscriptionSegment>.emitUtterance(utterance: SpeechUtterance) {
        val result = recognizer.recognize(utterance.samples, config.sampleRateHz)
        val text = result.text.trim()
        if (text.isEmpty()) {
            return
        }
        emit(
            TranscriptionSegment(
                text = text,
                startTimeMillis = utterance.startTimeMillis,
                endTimeMillis = utterance.endTimeMillis,
                isFinal = true,
                language = resolveLanguage(result.detectedLanguageTag)
            )
        )
    }

    private fun resolveLanguage(detectedTag: String?): Language? = if (config.language != Language.Auto) {
        config.language
    } else {
        detectedTag?.let { SenseVoiceLanguages.toLanguage(it) }
    }

    companion object {
        /** Stable id of the SenseVoice int8 model this engine expects. */
        const val MODEL_ID: String = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8"

        /** Default ONNX Runtime thread count. */
        const val DEFAULT_NUM_THREADS: Int = 1

        /**
         * Creates an engine reading the model from [modelDir].
         *
         * The directory must contain `model*.onnx` and `tokens.txt`; it is validated on first use,
         * before any native call, and a missing file yields `AiAndroidException.ModelAssetMissing`.
         */
        fun create(
            config: AsrConfig = AsrConfig(),
            modelDir: File,
            numThreads: Int = DEFAULT_NUM_THREADS
        ): SenseVoiceAsrEngine = SenseVoiceAsrEngine(
            config = config,
            recognizerProvider = {
                NativeSenseVoiceRecognizerFactory(MODEL_ID, modelDir, config.language, numThreads).create()
            },
            dispatcher = Dispatchers.Default
        )

        /** Test seam: builds an engine over an injected [recognizer] (no native). */
        internal fun forTesting(
            config: AsrConfig,
            recognizer: SenseVoiceRecognizer,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): SenseVoiceAsrEngine = SenseVoiceAsrEngine(config, { recognizer }, dispatcher)
    }
}
