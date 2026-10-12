package hk.edu.hkmu.sign.landmark

/**
 * A single 2D landmark in MediaPipe image-normalized coordinates.
 *
 * MediaPipe Tasks Vision reports `x` and `y` relative to the input image, so both are already in
 * `[0, 1]` for a fully in-frame landmark (they can fall outside when a hand is partially cropped).
 * The normalizer clamps them into the classifier's declared `[0, 1]` range.
 */
data class Keypoint2D(
    val x: Float,
    val y: Float
)

/**
 * A single 3D landmark: image-normalized [x]/[y] plus a wrist/hip-origin relative depth [z].
 *
 * The sign classifiers consume 2D landmarks only; [z] is retained so a caller or a future adapter can
 * use it, and it is dropped by [to2D].
 */
data class Keypoint3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    /** Projects this landmark onto the image plane, keeping only the normalized `x`/`y`. */
    fun to2D(): Keypoint2D = Keypoint2D(x, y)
}
