package hk.edu.hkmu.core.config

/**
 * Configuration for the local speaker-diarization engine.
 *
 * The defaults match the sherpa-onnx pyannote + speaker-embedding recipe and need no tuning for
 * typical two-to-four speaker audio.
 *
 * @property runtime Shared runtime knobs; see [EngineRuntimeConfig]. Diarization graphs are
 *   CPU-only, so an explicit GPU accelerator is refused at load.
 * @property sampleRateHz Input sample rate (Hz) the engine requires. Diarization models operate on
 *   16 kHz mono PCM.
 * @property numThreads ONNX Runtime intra-op thread count applied to both graphs.
 * @property numClusters Fixed speaker count, or `AUTO_CLUSTERS` to derive the count from
 *   [clusteringThreshold].
 * @property clusteringThreshold Cosine-distance threshold used for automatic clustering; lower
 *   values merge more aggressively.
 * @property minDurationOnSeconds Minimum speech duration (seconds) for a span to count as a speaker
 *   turn.
 * @property minDurationOffSeconds Minimum silence duration (seconds) that splits two speaker turns.
 */
data class DiarizationConfig(
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val numThreads: Int = DEFAULT_DIARIZATION_NUM_THREADS,
    val numClusters: Int = AUTO_CLUSTERS,
    val clusteringThreshold: Float = DEFAULT_CLUSTERING_THRESHOLD,
    val minDurationOnSeconds: Float = DEFAULT_MIN_DURATION_ON_SECONDS,
    val minDurationOffSeconds: Float = DEFAULT_MIN_DURATION_OFF_SECONDS
) {
    companion object {
        /** Sentinel [numClusters] value selecting threshold-based (automatic) clustering. */
        const val AUTO_CLUSTERS: Int = -1
    }
}

/** Default ONNX Runtime intra-op threads for a diarization graph. */
const val DEFAULT_DIARIZATION_NUM_THREADS: Int = 1

/** Default automatic-clustering cosine-distance threshold. */
const val DEFAULT_CLUSTERING_THRESHOLD: Float = 0.5f

/** Default minimum speech duration (seconds) for a speaker turn. */
const val DEFAULT_MIN_DURATION_ON_SECONDS: Float = 0.2f

/** Default minimum silence duration (seconds) that splits two speaker turns. */
const val DEFAULT_MIN_DURATION_OFF_SECONDS: Float = 0.5f
