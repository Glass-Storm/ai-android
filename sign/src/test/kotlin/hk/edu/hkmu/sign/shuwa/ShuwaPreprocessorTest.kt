package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.landmark.Keypoint3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the reference preprocessing port: frame selection, visibility masking and
 * resampling. No native library and no Android are involved, which is the point of keeping the
 * transform pure Kotlin.
 */
class ShuwaPreprocessorTest {

    @Test
    fun `uniform sampling matches the reference implementation`() {
        val indices = ShuwaPreprocessor.sampleIndices(nFrames = 20)

        assertEquals(
            listOf(1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18),
            indices.toList()
        )
    }

    @Test
    fun `sampling stays in range for every supported clip length`() {
        for (n in 2..40) {
            val indices = ShuwaPreprocessor.sampleIndices(nFrames = n)
            assertEquals(ShuwaSpec.WINDOW_FRAMES, indices.size)
            assertTrue("index below 1 for n=$n", indices.all { it >= 1 })
            assertTrue("index beyond the clip for n=$n", indices.all { it <= n - 1 })
        }
    }

    @Test
    fun `a prepared window has the four tensor shapes the translator expects`() {
        val window = ShuwaPreprocessor.prepare(frames(20))

        assertEquals(ShuwaInputs.POSE_SIZE, window.pose.size)
        assertEquals(ShuwaInputs.FACE_SIZE, window.face.size)
        assertEquals(ShuwaInputs.HAND_SIZE, window.leftHand.size)
        assertEquals(ShuwaInputs.HAND_SIZE, window.rightHand.size)
    }

    @Test
    fun `window slots follow the sampled frame order`() {
        val window = ShuwaPreprocessor.prepare(frames(20))

        // Frame i carries the value i, so slot 0 must hold frame 1 and slot 1 frame 2.
        assertEquals(1f, window.pose[0], TOLERANCE)
        val secondSlot = ShuwaSpec.POSE_ELEMENTS
        assertEquals(2f, window.pose[secondSlot], TOLERANCE)
    }

    @Test
    fun `an invisible hand is masked in both the pose and the hand block`() {
        val frames = frames(20) { 0.1f }
        val window = ShuwaPreprocessor.prepare(frames)

        val leftHandPoseSlot = ShuwaSpec.SELECTED_POSE.indexOf(ShuwaSpec.LEFT_HAND_IN_POSE[0])
        val firstSlotBase = leftHandPoseSlot * ShuwaSpec.POSE_CHANNELS
        assertEquals(ShuwaSpec.IGNORE_VALUE, window.pose[firstSlotBase], TOLERANCE)

        // The whole left-hand block for the first slot is masked as well.
        assertEquals(ShuwaSpec.IGNORE_VALUE, window.leftHand[0], TOLERANCE)
        assertEquals(ShuwaSpec.IGNORE_VALUE, window.leftHand[ShuwaSpec.HAND_ELEMENTS - 1], TOLERANCE)
    }

    @Test
    fun `a missing hand part becomes the sentinel instead of zeros`() {
        val frames = frames(20, withHands = false)
        val window = ShuwaPreprocessor.prepare(frames)

        assertEquals(ShuwaSpec.IGNORE_VALUE, window.leftHand[0], TOLERANCE)
        assertEquals(ShuwaSpec.IGNORE_VALUE, window.rightHand[0], TOLERANCE)
    }

    @Test
    fun `a single frame is rejected as malformed`() {
        val failure = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            ShuwaPreprocessor.prepare(frames(1))
        }

        assertTrue(failure.message.orEmpty().contains("at least 2 frames"))
    }

    private fun frames(
        count: Int,
        withHands: Boolean = true,
        leftVisibility: (Int) -> Float = { 0.9f }
    ): List<ShuwaFrame> = List(count) { index ->
        ShuwaFrame(
            pose =
            List(ShuwaSpec.POSE_POINTS) { point ->
                val visibility =
                    if (point in ShuwaSpec.LEFT_HAND_IN_POSE) leftVisibility(index) else 0.9f
                ShuwaPosePoint(
                    x = index.toFloat(),
                    y = point.toFloat(),
                    z = 0f,
                    visibility = visibility
                )
            },
            face = List(ShuwaSpec.FACE_POINTS) { point -> Keypoint3D(index.toFloat(), point.toFloat(), 0f) },
            leftHand = if (withHands) {
                List(ShuwaSpec.HAND_POINTS) { point -> Keypoint3D(index + 0.5f, point.toFloat(), 0f) }
            } else {
                emptyList()
            },
            rightHand = if (withHands) {
                List(ShuwaSpec.HAND_POINTS) { point -> Keypoint3D(index + 0.25f, point.toFloat(), 0f) }
            } else {
                emptyList()
            },
            timestampMillis = index.toLong() * 66L
        )
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
