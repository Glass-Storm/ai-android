package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.core.config.DEFAULT_SIGN_STRIDE
import hk.edu.hkmu.core.config.DEFAULT_SIGN_WINDOW_SIZE

/**
 * Tunables for [SignSegmenter].
 *
 * A continuous landmark stream is cut into fixed-size windows that the classifier consumes
 * (`windowSize` frames per window). Consecutive windows advance by `stride` frames, so they overlap
 * by `windowSize - stride` frames. The [motionThreshold] is the "transition gate": a window boundary
 * only produces a [SignWindow] when the mean per-frame landmark displacement inside that window
 * exceeds it, which keeps an idle (motionless) camera from emitting spurious signs.
 *
 * These are deliberately plain data so the same segmenter serves the TGCN (`windowSize = 50`) and the
 * SignBridge fingerspelling (`windowSize = 30`) models without per-model code.
 *
 * @property windowSize Number of landmark frames per window (model-driven; e.g. 50 or 30).
 * @property stride Frames advanced between consecutive boundaries; `1..windowSize`.
 * @property motionThreshold Strict lower bound on the window's mean displacement that opens the
 *   transition gate. `0f` (the default) means "any motion"; identical frames score exactly `0.0` and
 *   are never emitted.
 */
data class SignSegmenterConfig(
    val windowSize: Int = DEFAULT_SIGN_WINDOW_SIZE,
    val stride: Int = DEFAULT_SIGN_STRIDE,
    val motionThreshold: Float = DEFAULT_MOTION_THRESHOLD
) {
    /** Frames shared by two consecutive windows (`windowSize - stride`); `0` when non-overlapping. */
    val overlap: Int get() = windowSize - stride

    init {
        require(windowSize > 0) { "windowSize must be positive" }
        require(stride in 1..windowSize) { "stride must be in 1..windowSize" }
        require(motionThreshold.isFinite() && motionThreshold >= 0f) {
            "motionThreshold must be finite and non-negative"
        }
    }

    companion object {
        /** Gate threshold that admits any motion above an exactly-still window. */
        const val DEFAULT_MOTION_THRESHOLD: Float = 0f
    }
}
