package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.DiarizationConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.DiarizationEngine
import hk.edu.hkmu.core.engine.DiarizationSegment
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
 * [DiarizationEngine] backed by the sherpa-onnx offline speaker diarizer
 * (`OfflineSpeakerDiarization`): a pyannote segmentation graph plus a speaker-embedding graph,
 * clustered into speaker turns. Everything runs locally; there is no cloud call.
 *
 * ### Emission semantics
 * [diarize] accepts **16 kHz mono** PCM (`AudioChunk.samples` normalized Float32 in `[-1, 1]`) and
 * emits exactly one `DiarizationSegment` per detected speaker turn, in chronological order, then
 * completes. Empty audio emits nothing and completes. The flow is cold.
 *
 * ### Lifecycle
 * Buffering happens first; the license gate, the asset check and the native diarizer are created
 * **lazily at load** — only when non-empty audio reaches the flow (never at construction, so an
 * engine can be built without loading any weights). [close] releases the native session. The asset
 * layout is validated against [DiarizationModelAssets] before the first native call.
 *
 * @param config Engine configuration.
 * @param backendFactory Native seam; the production factory validates assets and builds the sherpa
 *   diarizer.
 * @param licenseGate License gate evaluated at load.
 * @param dispatcher Dispatcher the cold flow runs on.
 */
class SherpaDiarizationEngine internal constructor(
    private val config: DiarizationConfig,
    private val backendFactory: DiarizationBackendFactory,
    private val licenseGate: DiarizationLicenseGate,
    private val dispatcher: CoroutineDispatcher
) : DiarizationEngine, AutoCloseable {

    private val backendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        requireCpuOnly()
        licenseGate.ensureAllowed(MODEL_ID)
        backendFactory.create()
    }

    override fun diarize(audio: Flow<AudioChunk>): Flow<DiarizationSegment> = flow {
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
        backendDelegate.value.diarize(concat(chunks, totalSamples)).forEach { emit(it) }
    }.flowOn(dispatcher)

    /** Releases the native diarizer if it was created. Idempotent. */
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
        /** Stable id reported by the engine's license gate and asset errors. */
        const val MODEL_ID: String = DiarizationModels.PYANNOTE_3DSPEAKER_ID

        /**
         * Creates an engine reading the segmentation and embedding models from `modelDir`.
         *
         * `modelDir` must hold the layout documented on [DiarizationModels] (a
         * `sherpa-onnx-pyannote-segmentation-3-0/` directory plus the embedding `.onnx` at the root);
         * it is validated at load, before any native call, and a missing file yields
         * `AiAndroidException.ModelAssetMissing`. The license is evaluated at load: a restricted
         * policy/intent combination raises `AiAndroidException.LicenseRestricted`.
         */
        @Suppress("LongParameterList")
        fun create(
            config: DiarizationConfig = DiarizationConfig(),
            modelDir: File,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            spec: DiarizationModelSpec = DiarizationModels.Pyannote3Dspeaker,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): SherpaDiarizationEngine = SherpaDiarizationEngine(
            config = config,
            backendFactory = SherpaDiarizationBackendFactory(spec, modelDir, config),
            licenseGate = DiarizationLicenseGate(licensePolicy, intent),
            dispatcher = dispatcher
        )

        /** Test seam: builds an engine over an injected [backendFactory] (no native). */
        internal fun forTesting(
            config: DiarizationConfig,
            backendFactory: DiarizationBackendFactory,
            licenseGate: DiarizationLicenseGate = DiarizationLicenseGate(DefaultLicensePolicy(), UsageIntent.SelfUse),
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        ): SherpaDiarizationEngine = SherpaDiarizationEngine(config, backendFactory, licenseGate, dispatcher)
    }
}
