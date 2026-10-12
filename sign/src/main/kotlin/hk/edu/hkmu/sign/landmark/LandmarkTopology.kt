package hk.edu.hkmu.sign.landmark

/**
 * Describes the landmark ORDER a landmarker backend emits into a [LandmarkFrame].
 *
 * The sign classifiers consume a fixed per-frame keypoint count, but that count is a property of the
 * **classifier**, not of the extractor, so it lives here as data rather than a hardcoded constant:
 * the same normalization code serves a 55-keypoint TGCN stream and a 42-keypoint hands-only stream.
 *
 * ### S2 spec gap (open)
 * The Phase-0 spike (S2 §5) flagged that the WLASL TGCN's 55-keypoint
 * order is **unverified**: the WACV-2020 paper describes OpenPose 55 = 13 upper-body + 21L + 21R, but
 * the repo publishes no preprocessing code. [UPPER_BODY_HANDS] therefore uses a documented,
 * overridable default selection ([DEFAULT_UPPER_BODY_POSE_INDICES]); a caller who has verified a
 * different mapping must pass their own [LandmarkTopology] rather than editing this object.
 *
 * @property upperBodyPoseIndices Indices into the backend's pose landmark list, in classifier order.
 * @property handKeypointsPerSide Landmarks per hand (21 for both MediaPipe and OpenPose hands).
 */
data class LandmarkTopology(
    val upperBodyPoseIndices: List<Int>,
    val handKeypointsPerSide: Int
) {
    /** Total keypoints per frame, e.g. `13 + 21*2 = 55`. */
    val keypointsPerFrame: Int get() = upperBodyPoseIndices.size + handKeypointsPerSide * HANDS

    init {
        require(handKeypointsPerSide > 0) { "handKeypointsPerSide must be positive" }
        require(upperBodyPoseIndices.all { it >= 0 }) { "pose indices must be non-negative" }
        require(upperBodyPoseIndices.size <= BLAZEPOSE_LANDMARKS) { "pose selection exceeds BlazePose 33" }
    }

    companion object {
        /** Both hands are always modelled (left + right). */
        const val HANDS: Int = 2

        /** BlazePose emits 33 pose landmarks. */
        const val BLAZEPOSE_LANDMARKS: Int = 33

        /** OpenPose/MediaPipe hands have 21 landmarks. */
        const val HAND_LANDMARKS: Int = 21

        /**
         * BlazePose head+shoulder subset (indices 0..12: nose, eyes, ears, mouth corners, shoulders).
         * Documented placeholder pending the on-device verification of S2 gap #1; override if a
         * different upper-body selection is confirmed.
         */
        val DEFAULT_UPPER_BODY_POSE_INDICES: List<Int> = (0 until UPPER_BODY_COUNT).toList()

        /** 13 upper-body + 21 left + 21 right = 55 keypoints (thin-sliced WACV-2020 contract). */
        val UPPER_BODY_HANDS: LandmarkTopology = LandmarkTopology(
            upperBodyPoseIndices = DEFAULT_UPPER_BODY_POSE_INDICES,
            handKeypointsPerSide = HAND_LANDMARKS
        )

        /** 21 left + 21 right = 42 keypoints (hand-only extractor, no pose). */
        val HANDS_ONLY: LandmarkTopology = LandmarkTopology(
            upperBodyPoseIndices = emptyList(),
            handKeypointsPerSide = HAND_LANDMARKS
        )

        private const val UPPER_BODY_COUNT = 13
    }
}
