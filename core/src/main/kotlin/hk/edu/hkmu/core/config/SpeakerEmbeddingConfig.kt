package hk.edu.hkmu.core.config

/**
 * Configuration for the local speaker-embedding extractor (voiceprint enrollment).
 *
 * The extractor reuses the same sherpa-onnx speaker-embedding graph the diarizer bundles, so the
 * defaults need no tuning for typical 16 kHz mono speech.
 *
 * @property runtime Shared runtime knobs; see [EngineRuntimeConfig]. The embedding graph is
 *   CPU-only, so an explicit GPU accelerator is refused at load.
 * @property sampleRateHz Input sample rate (Hz) the extractor requires. The bundled CAM++ embedding
 *   model operates on 16 kHz mono PCM.
 * @property numThreads ONNX Runtime intra-op thread count applied to the embedding graph.
 */
data class SpeakerEmbeddingConfig(
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val numThreads: Int = DEFAULT_SPEAKER_EMBEDDING_NUM_THREADS
)

/** Default ONNX Runtime intra-op threads for the speaker-embedding graph. */
const val DEFAULT_SPEAKER_EMBEDDING_NUM_THREADS: Int = 1
