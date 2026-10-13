@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.sign

import hk.edu.hkmu.sign.landmark.Keypoint2D
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Deterministic, native-free synthetic [LandmarkFrame] streams for host-JVM (headless) tests.
 *
 * The production pipeline's landmark extraction boundary (MediaPipe) is device-only, so a headless
 * host CI cannot exercise the sign path from raw video. These helpers build the *assembled* landmark
 * frames that `SignPipeline.recognizeLandmarks` consumes, so segmentation → normalization →
 * classification can be driven with no device and no model.
 *
 * Every coordinate is a pure function of the frame's timestamp (and, where relevant, the frame
 * index), so a stream is byte-for-byte reproducible across runs and machines.
 */

/** Default keypoints per frame: 13 pose + 21 left hand + 21 right hand, matching the sign topology. */
const val DEFAULT_LANDMARK_KEYPOINTS: Int = 55

/** Default milliseconds between synthetic frames (~30 fps), matching a typical camera cadence. */
const val DEFAULT_LANDMARK_FRAME_INTERVAL_MILLIS: Long = 33L

/** Coordinate modulus keeping every synthetic coordinate inside `[0, 1]`. */
private const val COORDINATE_MODULUS = 100

/** Per-keypoint phase offset so neighbouring keypoints occupy distinct positions. */
private const val KEYPOINT_STRIDE = 7

/** Per-seed phase offset so different seeds yield different (but deterministic) patterns. */
private const val SEED_STRIDE = 13

/**
 * Seed used by [movingFrames]. The moving and resting [stillFrames] patterns differ by **motion**,
 * not by seed: both use seed `0`, but [movingFrames] advances the phase per frame while [stillFrames]
 * holds every frame identical.
 */
private const val MOVING_SEED = 0

/**
 * One deterministic [LandmarkFrame] of [count] keypoints, each in `[0, 1]`.
 *
 * The coordinates are a pure function of [timestampMillis] and [seed], so calling this with the same
 * arguments always returns an equal frame. Advancing [timestampMillis] moves the keypoints, which is
 * what the segmentation motion gate observes.
 *
 * @param count Number of keypoints in the frame; must be positive.
 * @param timestampMillis Presentation timestamp recorded on the frame.
 * @param seed Pattern selector; distinct seeds produce distinct deterministic layouts.
 */
fun landmarks(count: Int = DEFAULT_LANDMARK_KEYPOINTS, timestampMillis: Long = 0L, seed: Int = 0): LandmarkFrame {
    require(count > 0) { "count must be positive" }
    return frameAt(phase = timestampMillis, timestampMillis = timestampMillis, count = count, seed = seed)
}

/**
 * A deterministic **moving** sequence of [frames] frames that reliably clears the transition gate.
 *
 * Each frame's keypoints differ from the previous frame's, so a
 * [`hk.edu.hkmu.sign.segment.SignSegmenter`] with the default
 * `motionThreshold` of `0f` emits a window at every boundary — for both the 30-frame fingerspelling
 * and 50-frame TGCN window sizes.
 *
 * @param frames Number of frames to produce; must be non-negative.
 * @param keypoints Keypoints per frame.
 * @param frameIntervalMillis Milliseconds between consecutive frame timestamps.
 */
fun movingFrames(
    frames: Int,
    keypoints: Int = DEFAULT_LANDMARK_KEYPOINTS,
    frameIntervalMillis: Long = DEFAULT_LANDMARK_FRAME_INTERVAL_MILLIS
): List<LandmarkFrame> {
    require(frames >= 0) { "frames must be non-negative" }
    return List(frames) { index ->
        frameAt(
            phase = index.toLong(),
            timestampMillis = index.toLong() * frameIntervalMillis,
            count = keypoints,
            seed = MOVING_SEED
        )
    }
}

/**
 * A deterministic **motionless** sequence of [frames] frames that never opens the transition gate.
 *
 * Every frame carries the identical resting keypoints, so each window scores exactly `0.0` and a
 * [`hk.edu.hkmu.sign.segment.SignSegmenter`] emits nothing — the "idle camera produces no signs"
 * contract. Only the timestamps advance.
 *
 * @param frames Number of frames to produce; must be non-negative.
 * @param keypoints Keypoints per frame.
 * @param frameIntervalMillis Milliseconds between consecutive frame timestamps.
 */
fun stillFrames(
    frames: Int,
    keypoints: Int = DEFAULT_LANDMARK_KEYPOINTS,
    frameIntervalMillis: Long = DEFAULT_LANDMARK_FRAME_INTERVAL_MILLIS
): List<LandmarkFrame> {
    require(frames >= 0) { "frames must be non-negative" }
    val resting = frameAt(phase = 0L, timestampMillis = 0L, count = keypoints, seed = 0).keypoints
    return List(frames) { index ->
        LandmarkFrame(resting, timestampMillis = index.toLong() * frameIntervalMillis)
    }
}

/** Adapts a materialized [frames] list to the cold landmark flow the pipeline consumes. */
fun landmarkStream(frames: List<LandmarkFrame>): Flow<LandmarkFrame> = frames.asFlow()

/** Builds a frame whose keypoint coordinates derive from [phase] (plus [seed]) at [timestampMillis]. */
private fun frameAt(phase: Long, timestampMillis: Long, count: Int, seed: Int): LandmarkFrame {
    val base = phase + seed.toLong() * SEED_STRIDE
    val keypoints = List(count) { index ->
        val offset = base + index.toLong() * KEYPOINT_STRIDE
        Keypoint2D(
            x = unit(offset),
            y = unit(offset + KEYPOINT_STRIDE * count)
        )
    }
    return LandmarkFrame(keypoints, timestampMillis)
}

/** Maps [value] into `[0, 1)` via a non-negative modulus so coordinates stay in range. */
private fun unit(value: Long): Float {
    val wrapped = ((value % COORDINATE_MODULUS) + COORDINATE_MODULUS) % COORDINATE_MODULUS
    return wrapped.toFloat() / COORDINATE_MODULUS
}
