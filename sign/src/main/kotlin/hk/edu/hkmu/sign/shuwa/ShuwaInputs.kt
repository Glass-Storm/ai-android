package hk.edu.hkmu.sign.shuwa

/**
 * The four flat tensors one Shuwa window feeds to the translator, in frame-major order.
 *
 * Each array is laid out exactly as the TFLite graph expects for a single window:
 *
 * ```
 * pose     [1, 16, 15, 3]   -> WINDOW_FRAMES * SELECTED_POSE.size * 3 floats
 * face     [1, 16, 25, 3]   -> WINDOW_FRAMES * SELECTED_FACE.size * 3 floats
 * leftHand [1, 16, 21, 3]   -> WINDOW_FRAMES * HAND_POINTS * 3 floats
 * rightHand[1, 16, 21, 3]   -> WINDOW_FRAMES * HAND_POINTS * 3 floats
 * ```
 *
 * The index arithmetic is `((frame * points) + point) * channels + channel`, so a caller can rebuild
 * the nested view without a copy. Arrays are rejected at construction when their length does not
 * match [ShuwaSpec], which keeps a shape mistake from reaching the native interpreter.
 *
 * @property pose Selected pose coordinates, visibility channel already removed.
 * @property face Selected face coordinates.
 * @property leftHand Left-hand coordinates.
 * @property rightHand Right-hand coordinates.
 */
class ShuwaInputs(
    val pose: FloatArray,
    val face: FloatArray,
    val leftHand: FloatArray,
    val rightHand: FloatArray
) {
    init {
        require(pose.size == POSE_SIZE) { "pose must be $POSE_SIZE floats, was ${pose.size}" }
        require(face.size == FACE_SIZE) { "face must be $FACE_SIZE floats, was ${face.size}" }
        require(leftHand.size == HAND_SIZE) { "leftHand must be $HAND_SIZE floats, was ${leftHand.size}" }
        require(rightHand.size == HAND_SIZE) { "rightHand must be $HAND_SIZE floats, was ${rightHand.size}" }
    }

    /** Floats in the pose tensor. */
    companion object {
        /** Floats in the pose tensor. */
        val POSE_SIZE: Int = ShuwaSpec.WINDOW_FRAMES * ShuwaSpec.POSE_ELEMENTS

        /** Floats in the face tensor. */
        val FACE_SIZE: Int = ShuwaSpec.WINDOW_FRAMES * ShuwaSpec.FACE_ELEMENTS

        /** Floats in each hand tensor. */
        const val HAND_SIZE: Int = ShuwaSpec.WINDOW_FRAMES * ShuwaSpec.HAND_ELEMENTS
    }
}
