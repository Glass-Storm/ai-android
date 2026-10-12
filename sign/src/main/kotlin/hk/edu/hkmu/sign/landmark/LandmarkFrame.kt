package hk.edu.hkmu.sign.landmark

/**
 * One frame of landmarks already assembled into the classifier's per-frame keypoint order.
 *
 * The [keypoints] list length must equal the classifier topology's keypoint count; it is the input to
 * [LandmarkNormalizer], which enforces that contract and turns a window of frames into a tensor.
 *
 * @property keypoints Ordered per-frame keypoints (e.g. 13 upper-body + 21 left + 21 right).
 * @property timestampMillis Presentation timestamp in milliseconds.
 */
data class LandmarkFrame(
    val keypoints: List<Keypoint2D>,
    val timestampMillis: Long
)

/**
 * Raw, per-part landmarks as returned by a landmarker backend, BEFORE assembly into the classifier's
 * flat keypoint order.
 *
 * Keeping this MediaPipe-free is what lets the assembly + normalization half of the pipeline run in a
 * host JVM test with no native library. A hand-only backend leaves [pose] empty; a missing hand is an
 * empty list and is turned into a typed
 * [hk.edu.hkmu.core.error.AiAndroidException.MalformedFrame] by [LandmarkResultAssembler].
 *
 * @property pose Pose landmarks (33 for BlazePose), or empty for a hand-only backend.
 * @property leftHand Left-hand landmarks (21), or empty when absent.
 * @property rightHand Right-hand landmarks (21), or empty when absent.
 * @property timestampMillis Presentation timestamp in milliseconds.
 */
class RawLandmarkFrame(
    val pose: List<Keypoint3D>,
    val leftHand: List<Keypoint3D>,
    val rightHand: List<Keypoint3D>,
    val timestampMillis: Long
)
