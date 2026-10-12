@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.sign.landmark.Keypoint2D
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Deterministic synthetic landmark streams for the segmentation Tier-A tests. */
internal object SegmenterFixtures {

    /** Milliseconds between synthetic frames. */
    const val FRAME_PERIOD_MILLIS = 33L

    /** Per-frame horizontal step used to synthesise motion. */
    const val MOTION_STEP = 0.01f

    /** Default synthetic keypoints per frame. */
    const val DEFAULT_KEYPOINTS = 4

    /**
     * A frame whose every keypoint sits at `x = 0.5 + offset`: `offset == 0f` is motionless, and a
     * nonzero `offset` that changes between frames registers as motion.
     */
    fun frame(globalIndex: Int, offset: Float, keypoints: Int = DEFAULT_KEYPOINTS): LandmarkFrame {
        val points = (0 until keypoints).map { Keypoint2D(0.5f + offset, 0.5f) }
        return LandmarkFrame(points, timestampMillis = globalIndex.toLong() * FRAME_PERIOD_MILLIS)
    }

    /** A motionless frame ([offset] fixed at 0) with [count] keypoints. */
    fun idle(index: Int, count: Int = DEFAULT_KEYPOINTS): LandmarkFrame = frame(index, offset = 0f, keypoints = count)

    /**
     * Builds `count` frames whose horizontal offset is [offsetAt] of the global frame index, so a
     * stream can alternate motion bursts and idle stretches deterministically.
     */
    fun stream(count: Int, offsetAt: (Int) -> Float): List<LandmarkFrame> =
        (0 until count).map { frame(it, offsetAt(it)) }

    /** A stream in which every frame is identical (no motion). */
    fun idleStream(count: Int): List<LandmarkFrame> = (0 until count).map { idle(it) }

    /** Adapts a frame list to the [SignSegmenter] input flow. */
    fun streamOf(frames: List<LandmarkFrame>): Flow<LandmarkFrame> = frames.asFlow()
}
