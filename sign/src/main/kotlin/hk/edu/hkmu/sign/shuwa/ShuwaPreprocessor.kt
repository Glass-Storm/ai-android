package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.landmark.Keypoint3D
import kotlin.math.floor

/**
 * Turns a window of raw [ShuwaFrame]s into the translator's four input tensors.
 *
 * This is a line-for-line port of the reference pipeline's preprocessing, which is split across three
 * Python modules there:
 *
 * 1. `modules/holistic/holistic_manager.py` reports pose-with-visibility, face and hand landmarks, and
 *    substitutes a zero-filled block for any part that was not detected.
 * 2. `modules/utils/skeleton_utils.filter_visibility` masks unreliable hands with the model's
 *    [ShuwaSpec.IGNORE_VALUE] sentinel: hand-in-pose joints whose BlazePose visibility stays below
 *    [ShuwaSpec.HAND_VISIBILITY_THRESHOLD], and the hand block itself when the wrist is missing or the
 *    hand is not visible from the pose.
 * 3. `modules/utils/skeleton_utils.uniform_sampling` picks the window's frames evenly, then
 *    `apply_resampling` keeps them.
 *
 * The translator graph performs the remaining per-part normalisation (centring, shoulder/eye/hand
 * scaling and the right-hand mirror) internally, so this class deliberately stops at raw coordinates.
 *
 * Everything here is pure Kotlin with no native or Android dependency, so the whole transform is
 * exercised on the host JVM; only the landmark extractor is device-bound.
 *
 * @see ShuwaInputs
 * @see ShuwaSpec
 */
object ShuwaPreprocessor {

    /**
     * Prepares one window of frames for the translator.
     *
     * Frames may be in any order of capture but must be chronological. Missing parts are permitted —
     * they become the reference implementation's zero-filled blocks and are then masked.
     *
     * @param frames At least two captured frames; the window is resampled to [ShuwaSpec.WINDOW_FRAMES].
     * @return the four tensors described by [ShuwaInputs].
     * @throws AiAndroidException.MalformedFrame when fewer than two frames are supplied.
     */
    fun prepare(frames: List<ShuwaFrame>): ShuwaInputs {
        if (frames.size < MIN_FRAMES) {
            throw AiAndroidException.MalformedFrame(
                "a Shuwa window needs at least $MIN_FRAMES frames, got ${frames.size}"
            )
        }
        val sampled = sampleIndices(frames.size)
        val pose = FloatArray(ShuwaInputs.POSE_SIZE)
        val face = FloatArray(ShuwaInputs.FACE_SIZE)
        val left = FloatArray(ShuwaInputs.HAND_SIZE)
        val right = FloatArray(ShuwaInputs.HAND_SIZE)
        for ((slot, frameIndex) in sampled.withIndex()) {
            val parts = filteredParts(frames[frameIndex])
            writeSelected(
                source = parts.pose4,
                sourceStride = POSE_CHANNELS_WITH_VISIBILITY,
                selected = ShuwaSpec.SELECTED_POSE,
                slot = slot,
                target = pose
            )
            writeSelected(
                source = parts.face,
                sourceStride = ShuwaSpec.POSE_CHANNELS,
                selected = ShuwaSpec.SELECTED_FACE,
                slot = slot,
                target = face
            )
            copyInto(parts.leftHand, slot, left)
            copyInto(parts.rightHand, slot, right)
        }
        return ShuwaInputs(pose = pose, face = face, leftHand = left, rightHand = right)
    }

    /**
     * Frame indices the reference implementation's uniform sampler picks.
     *
     * `tick = (n - 2) / pick`, then `index(x) = int(tick / 2 + tick * x) + 1`. For 20 captured frames
     * and a 16-frame window that is `[1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18]`.
     *
     * @param nFrames Captured frames in the window, at least [MIN_FRAMES].
     * @param pick Output frames; defaults to the translator's window length.
     */
    fun sampleIndices(nFrames: Int, pick: Int = ShuwaSpec.WINDOW_FRAMES): IntArray {
        require(nFrames >= MIN_FRAMES) { "nFrames must be at least $MIN_FRAMES" }
        require(pick > 0) { "pick must be positive" }
        val tick = (nFrames - 2).toDouble() / pick
        return IntArray(pick) { index -> floor(tick / 2.0 + tick * index).toInt() + FIRST_INDEX }
    }

    /** One frame after the reference implementation's zero-fill and visibility masking. */
    private class FilteredParts(
        val pose4: FloatArray,
        val face: FloatArray,
        val leftHand: FloatArray,
        val rightHand: FloatArray
    )

    private fun filteredParts(frame: ShuwaFrame): FilteredParts {
        val pose4 = FloatArray(ShuwaSpec.POSE_POINTS * POSE_CHANNELS_WITH_VISIBILITY)
        frame.pose.forEachIndexed { index, point ->
            if (index < ShuwaSpec.POSE_POINTS) {
                val base = index * POSE_CHANNELS_WITH_VISIBILITY
                pose4[base] = point.x
                pose4[base + 1] = point.y
                pose4[base + 2] = point.z
                pose4[base + VISIBILITY_OFFSET] = point.visibility
            }
        }
        val face = FloatArray(ShuwaSpec.FACE_POINTS * ShuwaSpec.POSE_CHANNELS)
        copyPoints(frame.face, face, ShuwaSpec.FACE_POINTS)
        val left = FloatArray(ShuwaSpec.HAND_POINTS * ShuwaSpec.POSE_CHANNELS)
        copyPoints(frame.leftHand, left, ShuwaSpec.HAND_POINTS)
        val right = FloatArray(ShuwaSpec.HAND_POINTS * ShuwaSpec.POSE_CHANNELS)
        copyPoints(frame.rightHand, right, ShuwaSpec.HAND_POINTS)

        val missingLeftPose = isHandMissingFromPose(pose4, ShuwaSpec.LEFT_HAND_IN_POSE)
        val missingRightPose = isHandMissingFromPose(pose4, ShuwaSpec.RIGHT_HAND_IN_POSE)
        maskHandInPose(pose4, ShuwaSpec.LEFT_HAND_IN_POSE, missingLeftPose)
        maskHandInPose(pose4, ShuwaSpec.RIGHT_HAND_IN_POSE, missingRightPose)

        // Reference step 2: a zero wrist means the hand landmarks themselves are absent.
        maskHandInPose(pose4, ShuwaSpec.LEFT_HAND_IN_POSE, left[WRIST_X_OFFSET] == ShuwaSpec.MISSING_VALUE)
        maskHandInPose(pose4, ShuwaSpec.RIGHT_HAND_IN_POSE, right[WRIST_X_OFFSET] == ShuwaSpec.MISSING_VALUE)

        replaceZeros(left)
        replaceZeros(right)
        if (missingLeftPose) {
            left.fill(ShuwaSpec.IGNORE_VALUE)
        }
        if (missingRightPose) {
            right.fill(ShuwaSpec.IGNORE_VALUE)
        }
        return FilteredParts(pose4 = pose4, face = face, leftHand = left, rightHand = right)
    }

    /** Copies 3D points into a flat `(x, y, z)` block, leaving undetected slots at zero. */
    private fun copyPoints(points: List<Keypoint3D>, target: FloatArray, limit: Int) {
        points.forEachIndexed { index, point ->
            if (index < limit) {
                val base = index * ShuwaSpec.POSE_CHANNELS
                target[base] = point.x
                target[base + 1] = point.y
                target[base + 2] = point.z
            }
        }
    }

    /** True when every one of [indices] is below the hand visibility threshold. */
    private fun isHandMissingFromPose(pose4: FloatArray, indices: IntArray): Boolean = indices.all { index ->
        pose4[index * POSE_CHANNELS_WITH_VISIBILITY + VISIBILITY_OFFSET] <
            ShuwaSpec.HAND_VISIBILITY_THRESHOLD
    }

    /** Writes the masking sentinel over every coordinate of [indices] when [missing]. */
    private fun maskHandInPose(pose4: FloatArray, indices: IntArray, missing: Boolean) {
        if (!missing) {
            return
        }
        indices.forEach { index ->
            val base = index * POSE_CHANNELS_WITH_VISIBILITY
            for (channel in 0 until POSE_CHANNELS_WITH_VISIBILITY) {
                pose4[base + channel] = ShuwaSpec.IGNORE_VALUE
            }
        }
    }

    /** Reference behaviour: any coordinate equal to the missing value becomes the sentinel. */
    private fun replaceZeros(values: FloatArray) {
        for (index in values.indices) {
            if (values[index] == ShuwaSpec.MISSING_VALUE) {
                values[index] = ShuwaSpec.IGNORE_VALUE
            }
        }
    }

    /** Writes one frame's selected 3-component points into [target] at window slot [slot]. */
    private fun writeSelected(
        source: FloatArray,
        sourceStride: Int,
        selected: IntArray,
        slot: Int,
        target: FloatArray
    ) {
        var out = slot * selected.size * ShuwaSpec.POSE_CHANNELS
        selected.forEach { index ->
            val base = index * sourceStride
            for (channel in 0 until ShuwaSpec.POSE_CHANNELS) {
                target[out++] = source[base + channel]
            }
        }
    }

    /** Writes one frame's flat component block into [target] at window slot [slot]. */
    private fun copyInto(source: FloatArray, slot: Int, target: FloatArray) {
        System.arraycopy(source, 0, target, slot * source.size, source.size)
    }

    private const val MIN_FRAMES = 2
    private const val FIRST_INDEX = 1
    private const val POSE_CHANNELS_WITH_VISIBILITY = 4
    private const val VISIBILITY_OFFSET = 3
    private const val WRIST_X_OFFSET = 0
}
