package hk.edu.hkmu.sign.landmark

import android.graphics.Bitmap

/**
 * One caller-supplied video frame.
 *
 * The library **never** opens a camera or a decoder: the host owns capture and hands over an already
 * decoded [image] plus its presentation [timestampMillis]. This keeps the sign module a pure
 * transform with no Android lifecycle.
 *
 * @property image Decoded frame bitmap (any size; MediaPipe resizes internally).
 * @property timestampMillis Presentation timestamp in milliseconds.
 */
class VideoFrame(
    val image: Bitmap,
    val timestampMillis: Long
)

/** Which MediaPipe landmarker graph backs an extractor. */
enum class LandmarkerMode {
    /** `holistic_landmarker.task`: pose + both hands (the 55-keypoint contract). */
    Holistic,

    /** `hand_landmarker.task`: hands only, `num_hands` hands (the 42-keypoint contract). */
    Hand
}

/**
 * Narrow internal seam over a native landmarker backend.
 *
 * It emits only [RawLandmarkFrame] (MediaPipe-free), so the production [MediaPipeLandmarkerBackend]
 * can be substituted by a fake in a host JVM test and the registry lifecycle exercised without a
 * native library. The production implementation is created lazily by the model loader.
 */
internal interface LandmarkerBackend : AutoCloseable {

    /** Runs the landmarker on one frame and returns the raw per-part landmarks. */
    fun detect(image: Bitmap, timestampMillis: Long): RawLandmarkFrame

    /** Releases the underlying native landmarker. Idempotent. */
    override fun close() {}
}

/**
 * Public extraction seam: caller-supplied frame in, assembled [LandmarkFrame] out.
 *
 * The production implementation ([MediaPipeLandmarkExtractor]) is device-only. Tests and hosts can
 * implement this directly to feed synthetic landmarks into the segmentation/classification pipeline
 * with no MediaPipe native.
 */
interface LandmarkExtractor : AutoCloseable {

    /** Extracts the assembled landmark frame for [frame]. */
    fun extract(frame: VideoFrame): LandmarkFrame

    /** Releases the underlying native landmarker. Idempotent. */
    override fun close() {}
}
