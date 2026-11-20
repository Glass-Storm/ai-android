@file:Suppress("MagicNumber")

// This file IS the numeric contract: the joint-index tables and thresholds are copied verbatim from
// the reference model's configuration, so their literals are data, not unexplained constants.

package hk.edu.hkmu.sign.shuwa

/**
 * The fixed numeric contract of the Shuwa HKSL translator.
 *
 * Every value here is copied from the reference implementation's configuration
 * (`configs/skeletons.gin`, `configs/utils.gin` and `modules/holistic/holistic_manager.py`) and must
 * not be tuned independently: the reference weights were trained against exactly these selections,
 * thresholds and sentinels.
 *
 * The index arrays are internal so a host cannot mutate the numeric contract at runtime.
 *
 * @see ShuwaPreprocessor
 */
object ShuwaSpec {

    /** Pose points MediaPipe Holistic emits per frame. */
    const val POSE_POINTS: Int = 33

    /** Face points MediaPipe Holistic emits per frame. */
    const val FACE_POINTS: Int = 468

    /** Landmarks per hand. */
    const val HAND_POINTS: Int = 21

    /** Frames per classifier window (the translator's fixed input length). */
    const val WINDOW_FRAMES: Int = 16

    /** Dimensions of the translator's embedding output. */
    const val EMBEDDING_DIMENSIONS: Int = 336

    /** Pose joints kept from the 33 BlazePose points, in output order. */
    internal val SELECTED_POSE: IntArray = intArrayOf(0, 2, 5, 7, 8, 11, 12, 15, 16, 17, 18, 19, 20, 21, 22)

    /** Face points kept from the 468-point mesh: nose, lip contour, and both eyes. */
    internal val SELECTED_FACE: IntArray =
        intArrayOf(
            1,
            78, 191, 80, 13, 310, 415, 308, 324, 318, 14, 88, 95,
            107, 69, 105, 52, 159, 145,
            336, 299, 334, 282, 386, 374
        )

    /** Positions in [SELECTED_POSE] that duplicate the LEFT hand. */
    internal val LEFT_HAND_IN_POSE: IntArray = intArrayOf(7, 9, 11, 13)

    /** Positions in [SELECTED_POSE] that duplicate the RIGHT hand. */
    internal val RIGHT_HAND_IN_POSE: IntArray = intArrayOf(8, 10, 12, 14)

    /** Raw BlazePose indices for the LEFT hand in [SELECTED_POSE]. */
    internal val RAW_LEFT_HAND_IN_POSE: IntArray =
        LEFT_HAND_IN_POSE.map { SELECTED_POSE[it] }.toIntArray()

    /** Raw BlazePose indices for the RIGHT hand in [SELECTED_POSE]. */
    internal val RAW_RIGHT_HAND_IN_POSE: IntArray =
        RIGHT_HAND_IN_POSE.map { SELECTED_POSE[it] }.toIntArray()

    /** BlazePose visibility below which a hand is considered missing. */
    const val HAND_VISIBILITY_THRESHOLD: Float = 0.7f

    /** Value MediaPipe uses for "not detected" (and the sentinel the filters search for). */
    const val MISSING_VALUE: Float = 0f

    /** Sentinel written for joints the model must ignore (its masking value). */
    const val IGNORE_VALUE: Float = -100f

    /** Landmarks per selected pose point in the classifier input (`x`, `y`, `z`). */
    const val POSE_CHANNELS: Int = 3

    /** Floats per selected pose point once the visibility channel is dropped. */
    val POSE_ELEMENTS: Int = SELECTED_POSE.size * POSE_CHANNELS

    /** Floats per selected face point (`x`, `y`, `z`). */
    val FACE_ELEMENTS: Int = SELECTED_FACE.size * POSE_CHANNELS

    /** Floats per hand (`x`, `y`, `z`). */
    const val HAND_ELEMENTS: Int = HAND_POINTS * POSE_CHANNELS
}
