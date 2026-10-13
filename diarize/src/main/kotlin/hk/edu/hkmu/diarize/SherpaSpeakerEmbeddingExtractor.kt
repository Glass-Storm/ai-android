package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.SpeakerEmbeddingConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.SpeakerEmbeddingExtractor
import hk.edu.hkmu.core.engine.Voiceprint
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [SpeakerEmbeddingExtractor] backed by the sherpa-onnx standalone speaker-embedding extractor
 * (`SpeakerEmbeddingExtractor`): the same 3D-Speaker CAM++ graph the diarizer bundles, driven
 * directly to turn one speaker's audio into a reusable [Voiceprint]. Everything runs locally; there
 * is no cloud call, no new native runtime and no new dependency.
 *
 * ### Emission semantics
 * [extract] accepts **16 kHz mono** PCM (`AudioChunk.samples` normalized Float32 in `[-1, 1]`) and
 * emits exactly one [Voiceprint] for the whole utterance, then completes. Empty audio emits nothing
 * and completes. The flow is cold.
 *
 * ### Lifecycle
 * Buffering happens first; the license gate, the asset check and the native extractor are created
 * **lazily at load** — only when non-empty audio reaches the flow (never at construction, so an
 * extractor can be built without loading any weights). [close] releases the native session. The asset
 * layout is validated against [DiarizationModelAssets] before the first native call.
 *
 * @param config Engine configuration.
 * @param backendFactory Native seam; the production factory validates assets and builds the sherpa
 *   extractor.
 * @param licenseGate License gate evaluated at load.
 * @param dispatcher Dispatcher the cold flow runs on.
 */
class SherpaSpeakerEmbeddingExtractor internal constructor(
    private val config: SpeakerEmbeddingConfig,
    private val backendFactory: SpeakerEmbeddingBackendFactory,
    private val licenseGate: DiarizationLicenseGate,
    private val dispatcher: CoroutineDispatcher
) : SpeakerEmbeddingExtractor, AutoCloseable {

    private val backendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        requireCpuOnly()
        licenseGate.ensureAllowed(MODEL_ID)
        backendFactory.create()
    }

    override fun extract(audio: Flow<AudioChunk>): Flow<Voiceprint> = flow {
        val chunks = ArrayList<FloatArray>()
        var totalSamples = 0
        audio.collect { chunk ->
            requireSampleRate(chunk)
            chunks += chunk.samples
            totalSamples += chunk.samples.size
        }
        if (totalSamples == 0) {
            return@flow
        }
        val embedding = backendDelegate.value.embed(concat(chunks, totalSamples))
        emit(Voiceprint(embedding = embedding, modelId = MODEL_ID))
    }.flowOn(dispatcher)

    /** Releases the native extractor if it was created. Idempotent. */
    override fun close() {
        if (backendDelegate.isInitialized()) {
            backendDelegate.value.close()
        }
    }

    private fun requireCpuOnly() {
        if (config.runtime.accelerator == Accelerator.Gpu) {
            throw AiAndroidException.AcceleratorUnavailable(Accelerator.Gpu, listOf(Accelerator.Cpu))
        }
    }

    private fun requireSampleRate(chunk: AudioChunk) {
        if (chunk.sampleRateHz != config.sampleRateHz) {
            throw AiAndroidException.MalformedFrame(
                "expected ${config.sampleRateHz} Hz mono PCM, got ${chunk.sampleRateHz} Hz"
            )
        }
    }

    private fun concat(chunks: List<FloatArray>, totalSamples: Int): FloatArray {
        val samples = FloatArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        return samples
    }

    companion object {
        /** Stable id reported by the extractor's license gate and asset errors. */
        const val MODEL_ID: String = DiarizationModels.PYANNOTE_3DSPEAKER_ID

        /**
         * Creates an extractor reading the embedding model from `modelDir`.
         *
         * `modelDir` must hold the layout documented on [DiarizationModels] (the CAM++ embedding
         * `.onnx` at the root); it is validated at load, before any native call, and a missing file
         * yields `AiAndroidException.ModelAssetMissing`. Only the embedding graph is required — the
         * segmentation graph the diarizer needs is unused here. The license is evaluated at load: a
         * restricted policy/intent combination raises `AiAndroidException.LicenseRestricted`.
         */
        @Suppress("LongParameterList")
        fun create(
            config: SpeakerEmbeddingConfig = SpeakerEmbeddingConfig(),
            modelDir: File,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            spec: DiarizationModelSpec = DiarizationModels.Pyannote3Dspeaker,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): SherpaSpeakerEmbeddingExtractor = SherpaSpeakerEmbeddingExtractor(
            config = config,
            backendFactory = SherpaSpeakerEmbeddingBackendFactory(spec, modelDir, config),
            licenseGate = DiarizationLicenseGate(licensePolicy, intent),
            dispatcher = dispatcher
        )

        /** Test seam: builds an extractor over an injected [backendFactory] (no native). */
        internal fun forTesting(
            config: SpeakerEmbeddingConfig,
            backendFactory: SpeakerEmbeddingBackendFactory,
            licenseGate: DiarizationLicenseGate = DiarizationLicenseGate(DefaultLicensePolicy(), UsageIntent.SelfUse),
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        ): SherpaSpeakerEmbeddingExtractor =
            SherpaSpeakerEmbeddingExtractor(config, backendFactory, licenseGate, dispatcher)
    }
}
