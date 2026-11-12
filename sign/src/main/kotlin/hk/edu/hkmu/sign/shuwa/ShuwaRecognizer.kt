package hk.edu.hkmu.sign.shuwa

import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Tunables for [ShuwaRecognizer].
 *
 * Every field is a deliberate configuration point: the window length must match the provisioned
 * translator, while the stride, motion gate and confidence threshold are recogniser policy.
 *
 * @property width Frames per window; must equal the translator's fixed input length.
 * @property stride Frames advanced between windows; `1..width`.
 * @property motionThreshold Mean per-frame pose displacement below which a window is skipped, so an
 *   idle camera never emits.
 * @property confidenceThreshold Confidence below which a result is still emitted but flagged.
 */
data class ShuwaRecognizerConfig(
    val width: Int = ShuwaSpec.WINDOW_FRAMES,
    val stride: Int = DEFAULT_STRIDE,
    val motionThreshold: Float = DEFAULT_MOTION_THRESHOLD,
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) {
    init {
        require(width >= MIN_WIDTH) { "width must be at least $MIN_WIDTH" }
        require(stride in 1..width) { "stride must be in 1..width" }
    }

    companion object {
        /** Frames advanced between windows (75% overlap at the 16-frame width). */
        const val DEFAULT_STRIDE: Int = 4

        /** Motion gate that admits any perceptible movement. */
        const val DEFAULT_MOTION_THRESHOLD: Float = 0.004f

        /** Confidence below which a result is still emitted, but flagged. */
        const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.5f

        private const val MIN_WIDTH = 2
    }
}

/**
 * One recognised HKSL gloss emitted at a window boundary.
 *
 * @property label Winning gloss from the KNN table (a `Hksl_*` label).
 * @property confidence Neighbour-weighted confidence in `[0, 1]`.
 * @property distance Euclidean distance to the nearest stored embedding.
 * @property windowStartMillis Timestamp of the window's first frame.
 * @property windowEndMillis Timestamp of the window's last frame.
 * @property lowConfidence True when the confidence is below the configured threshold; still emitted so
 *   a consumer can choose to drop it, but the host UI should not present it as a live subtitle.
 */
data class ShuwaPrediction(
    val label: String,
    val confidence: Float,
    val distance: Float,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val lowConfidence: Boolean
)

/**
 * Frame-to-gloss facade for the Shuwa HKSL translator: window -> [ShuwaPreprocessor] -> embedding -> KNN.
 *
 * ### Windowing
 * A window holds [ShuwaRecognizerConfig.width] consecutive frames and advances by
 * [ShuwaRecognizerConfig.stride] frames after each evaluation, so consecutive windows overlap. One
 * [ShuwaPrediction] is emitted per **motion-gated** window, in capture order; a window whose pose
 * barely changed is skipped, which is what keeps an idle camera from emitting glosses. The flow
 * completes with the input flow, and cancelling collection discards the in-progress window.
 *
 * ### Threading
 * Preprocessing, inference and the KNN lookup run on [dispatcher], so a slow collector throttles the
 * producer instead of dropping frames.
 *
 * ### Lifecycle
 * [close] releases the embedding model when it is [AutoCloseable]; the index holds no native handle.
 *
 * @property embedder translator wrapper (production: [TfliteShuwaEmbedder]).
 * @property index HKSL-only KNN table.
 * @property config window/motion/confidence policy.
 * @property dispatcher worker for the CPU-bound stages.
 */
class ShuwaRecognizer(
    private val embedder: ShuwaEmbeddingModel,
    private val index: ShuwaKnnIndex,
    private val config: ShuwaRecognizerConfig = ShuwaRecognizerConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AutoCloseable {

    /**
     * Consumes [frames] and emits one prediction per motion-gated window.
     *
     * Missing landmarks are tolerated: the preprocessor masks them with the model's sentinel, matching
     * the reference implementation.
     */
    fun recognize(frames: Flow<ShuwaFrame>): Flow<ShuwaPrediction> = flow {
        val window = ArrayDeque<ShuwaFrame>(config.width)
        frames.collect { frame ->
            window.addLast(frame)
            if (window.size < config.width) {
                return@collect
            }
            if (meanMotion(window) > config.motionThreshold) {
                emit(predict(window.toList()))
            }
            repeat(config.stride) {
                if (window.isNotEmpty()) {
                    window.removeFirst()
                }
            }
        }
    }.flowOn(dispatcher)

    /** Releases the embedding model when it owns native resources. */
    override fun close() {
        (embedder as? AutoCloseable)?.close()
    }

    private fun predict(frames: List<ShuwaFrame>): ShuwaPrediction {
        val embedding = embedder.embed(ShuwaPreprocessor.prepare(frames))
        val match = index.match(embedding)
        return ShuwaPrediction(
            label = match.label,
            confidence = match.confidence,
            distance = match.distance,
            windowStartMillis = frames.first().timestampMillis,
            windowEndMillis = frames.last().timestampMillis,
            lowConfidence = match.confidence < config.confidenceThreshold
        )
    }

    /** Mean per-pose-point displacement between consecutive frames; `0` when no pose is available. */
    private fun meanMotion(window: Collection<ShuwaFrame>): Float {
        var total = 0.0
        var comparisons = 0
        var previous: ShuwaFrame? = null
        window.forEach { frame ->
            val prior = previous
            if (prior != null && prior.pose.isNotEmpty() && frame.pose.isNotEmpty()) {
                val count = minOf(prior.pose.size, frame.pose.size)
                for (index in 0 until count) {
                    val dx = (frame.pose[index].x - prior.pose[index].x).toDouble()
                    val dy = (frame.pose[index].y - prior.pose[index].y).toDouble()
                    total += sqrt(dx * dx + dy * dy)
                    comparisons++
                }
            }
            previous = frame
        }
        return if (comparisons == 0) 0f else (total / comparisons).toFloat()
    }
}
