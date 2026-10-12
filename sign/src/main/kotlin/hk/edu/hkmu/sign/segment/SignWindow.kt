package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.sign.landmark.LandmarkFrame

/**
 * One segmented window of landmark frames, emitted by [SignSegmenter] at a window boundary.
 *
 * This is both the recognised unit of work and the boundary event: [startMillis]/[endMillis] locate
 * the window on the input timeline, [index] is its 0-based ordinal in the stream, and [motionScore]
 * is the transition-gate observable that admitted it. [frames] is exactly `windowSize` consecutive
 * [LandmarkFrame]s, in stream order, ready to be fed to the classifier's normalization step.
 *
 * @property index 0-based boundary ordinal within one stream.
 * @property frames The `windowSize` frames of the window, oldest first.
 * @property startMillis Timestamp of the window's first frame.
 * @property endMillis Timestamp of the window's last frame.
 * @property motionScore Mean per-frame landmark displacement across the window (`> motionThreshold`).
 */
data class SignWindow(
    val index: Int,
    val frames: List<LandmarkFrame>,
    val startMillis: Long,
    val endMillis: Long,
    val motionScore: Float
)
