package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.sign.landmark.Keypoint3D

/**
 * One pose landmark as MediaPipe Holistic reports it.
 *
 * The Shuwa translator needs the BlazePose visibility score, which [Keypoint3D] does not carry, so
 * pose points have their own type. Coordinates are MediaPipe image-normalized (`x`/`y` in `[0, 1]`
 * for an in-frame point) with a hip-origin relative depth [z], and [visibility] in `[0, 1]`.
 *
 * @property x Image-normalized horizontal position.
 * @property y Image-normalized vertical position.
 * @property z Hip-origin relative depth.
 * @property visibility BlazePose visibility score in `[0, 1]`.
 */
data class ShuwaPosePoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

/**
 * One raw frame for the Shuwa pipeline: pose + face + both hands, **before** joint selection,
 * visibility filtering and resampling.
 *
 * This is deliberately raw. Shuwa does not consume a flat 2D keypoint list like the ASL classifiers:
 * it needs 33 pose points (with visibility), 468 face points, and one 21-point hand per side, then
 * selects and normalises them itself. Keeping the parts separate is what lets
 * [ShuwaPreprocessor] mirror the reference implementation exactly and stay host-JVM testable.
 *
 * A missing part is an **empty list**, never a partially filled one: [ShuwaPreprocessor] turns that
 * into the reference implementation's zero-filled block, exactly as MediaPipe reports "not detected".
 *
 * @property pose Pose landmarks (33 for BlazePose), or empty when no pose was detected.
 * @property face Face landmarks (468), or empty when no face was detected.
 * @property leftHand Left-hand landmarks (21), or empty when the hand was not detected.
 * @property rightHand Right-hand landmarks (21), or empty when the hand was not detected.
 * @property timestampMillis Presentation timestamp in milliseconds.
 */
class ShuwaFrame(
    val pose: List<ShuwaPosePoint>,
    val face: List<Keypoint3D>,
    val leftHand: List<Keypoint3D>,
    val rightHand: List<Keypoint3D>,
    val timestampMillis: Long
)
