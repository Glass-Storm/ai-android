package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.sign.landmark.Keypoint2D
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import kotlin.math.sqrt

/**
 * Transition/motion gate for [SignSegmenter].
 *
 * The score is the mean Euclidean displacement of every keypoint between consecutive frames of the
 * window. A window of identical frames scores exactly `0.0`; sustained hand/body motion raises it. The
 * gate opens only when the score is **strictly greater** than the configured threshold, so an idle
 * stream never emits (the adversarial `no-motion => zero boundaries` contract).
 *
 * Pure Kotlin with no native or Android dependency, so the gate math is fully host-JVM testable.
 */
internal object MotionGate {

    /**
     * Mean per-keypoint displacement over [window]'s consecutive frame pairs.
     *
     * A window with fewer than two frames scores `0.0` (nothing moved). Keypoint counts are assumed
     * consistent within the window; [SignSegmenter] enforces that before scoring.
     */
    fun score(window: List<LandmarkFrame>): Float {
        if (window.size < MIN_FRAMES_FOR_MOTION) return 0f
        var totalDisplacement = 0.0
        var comparisons = 0
        for (frame in 1 until window.size) {
            val previous = window[frame - 1].keypoints
            val current = window[frame].keypoints
            for (keypoint in previous.indices) {
                totalDisplacement += displacement(previous[keypoint].x, previous[keypoint].y, current[keypoint])
                comparisons++
            }
        }
        return if (comparisons == 0) 0f else (totalDisplacement / comparisons).toFloat()
    }

    /** True when [score] clears [threshold] (strict), i.e. the window contains motion. */
    fun isOpen(score: Float, threshold: Float): Boolean = score > threshold

    private fun displacement(previousX: Float, previousY: Float, current: Keypoint2D): Double {
        val deltaX = (current.x - previousX).toDouble()
        val deltaY = (current.y - previousY).toDouble()
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private const val MIN_FRAMES_FOR_MOTION = 2
}
