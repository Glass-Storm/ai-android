package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.config.DEFAULT_SIGN_STRIDE
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.sign.model.SignLanguages

/**
 * Configuration for [SignPipeline].
 *
 * The window length is **not** configured here: it is the classifier's contract
 * ([hk.edu.hkmu.sign.model.SignModelSpec.windowFrames]) and the pipeline derives the segmenter's
 * window from it so the normalization step always receives a full window. Only the cadence
 * ([stride]) and the transition gate ([motionThreshold]) are caller-controlled.
 *
 * @property classifierId Registry id of the classifier to serve; when blank the pipeline resolves
 *   the first classifier for [language].
 * @property language Sign language used to resolve [classifierId] when it is blank.
 * @property stride Frames advanced between consecutive windows; coerced into `1..windowFrames`.
 * @property motionThreshold Transition-gate threshold; a window is only classified when its mean
 *   landmark displacement is strictly greater (see `SignSegmenter`).
 * @property confidenceThreshold Softmax confidence below which a result is flagged
 *   [SignResult.lowConfidence] and recombination is skipped.
 * @property recombinationEnabled When false the post-processor is bypassed even if one is supplied
 *   (opt-out, mirroring `PostProcessConfig.recombinationEnabled`).
 */
@Suppress("LongParameterList")
data class SignPipelineConfig(
    val classifierId: String,
    val language: Language = SignLanguages.Asl,
    val stride: Int = DEFAULT_SIGN_STRIDE,
    val motionThreshold: Float = DEFAULT_MOTION_THRESHOLD,
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    val recombinationEnabled: Boolean = true
) {
    init {
        require(confidenceThreshold in 0f..1f) { "confidenceThreshold must be within [0, 1]" }
        require(motionThreshold.isFinite() && motionThreshold >= 0f) {
            "motionThreshold must be finite and non-negative"
        }
    }

    companion object {
        /** Any motion above an exactly-still window opens the transition gate. */
        const val DEFAULT_MOTION_THRESHOLD: Float = 0f

        /** Below this softmax confidence a result is flagged low-confidence. */
        const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.5f
    }
}
