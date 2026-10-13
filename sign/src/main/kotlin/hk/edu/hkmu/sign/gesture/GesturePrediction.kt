package hk.edu.hkmu.sign.gesture

/**
 * One static hand-gesture recognition on a single image.
 *
 * This is the MediaPipe Tasks Vision canned-gesture label for the most confident hand in a frame,
 * e.g. `Thumb_Up`, `Victory`, `Closed_Fist`. It is a **static** per-image result, not a sign
 * sequence: a host decides how to aggregate consecutive frames.
 *
 * @property label MediaPipe gesture category name (`""` is never surfaced — `recognize` returns
 *   `null` instead when no gesture is present).
 * @property score Confidence in `[0, 1]` for [label].
 * @property handedness Reported hand side (`"Left"`/`"Right"`), or `null` when unlabeled. MediaPipe
 *   reports this assuming a mirrored (selfie) frame, so a third-person view may need flipping.
 * @property handCount Number of hands for which a gesture was produced in the frame.
 */
data class GesturePrediction(
    val label: String,
    val score: Float,
    val handedness: String?,
    val handCount: Int
)
