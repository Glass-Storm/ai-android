@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tier A: the MediaPipe-free assembly seam (`RawLandmarkFrame` → `LandmarkFrame`) and its typed
 * failure when a required part is missing.
 */
class LandmarkResultAssemblerTest {

    private val holistic = LandmarkResultAssembler(LandmarkTopology.UPPER_BODY_HANDS)
    private val handsOnly = LandmarkResultAssembler(LandmarkTopology.HANDS_ONLY)

    @Test
    fun `holistic frame assembles 13 upper-body plus both hands in order`() {
        val frame = holistic.assemble(raw(pose = 33, left = 21, right = 21, timestamp = 123L))

        assertEquals(55, frame.keypoints.size)
        assertEquals(123L, frame.timestampMillis)
        // First 13 keypoints come from the selected pose indices (0..12).
        for (index in 0 until 13) {
            assertEquals(poseX(index).x, frame.keypoints[index].x, 0f)
        }
        // Left hand starts right after the upper-body block.
        assertEquals(leftX(0).x, frame.keypoints[13].x, 0f)
        assertEquals(rightX(0).x, frame.keypoints[13 + 21].x, 0f)
    }

    @Test
    fun `hands-only topology assembles 42 keypoints with no pose`() {
        val frame = handsOnly.assemble(raw(pose = 0, left = 21, right = 21, timestamp = 0L))

        assertEquals(42, frame.keypoints.size)
        assertEquals(leftX(0).x, frame.keypoints[0].x, 0f)
        assertEquals(rightX(0).x, frame.keypoints[21].x, 0f)
    }

    @Test
    fun `a missing hand throws MalformedFrame`() {
        val raw = raw(pose = 33, left = 21, right = 0, timestamp = 0L)

        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) { holistic.assemble(raw) }
        assertEquals("missing right hand landmarks: 0/21", exception.reason)
    }

    @Test
    fun `a truncated pose throws MalformedFrame`() {
        val raw = raw(pose = 5, left = 21, right = 21, timestamp = 0L)

        assertThrows(AiAndroidException.MalformedFrame::class.java) { holistic.assemble(raw) }
    }

    private fun raw(pose: Int, left: Int, right: Int, timestamp: Long): RawLandmarkFrame = RawLandmarkFrame(
        pose = (0 until pose).map { poseX(it) },
        leftHand = (0 until left).map { leftX(it) },
        rightHand = (0 until right).map { rightX(it) },
        timestampMillis = timestamp
    )

    private fun poseX(index: Int): Keypoint3D = Keypoint3D(index.toFloat(), POSE_Y, 0f)

    private fun leftX(index: Int): Keypoint3D = Keypoint3D(LEFT_BASE + index, LEFT_BASE, 0f)

    private fun rightX(index: Int): Keypoint3D = Keypoint3D(RIGHT_BASE + index, RIGHT_BASE, 0f)

    private companion object {
        const val POSE_Y = 0.1f
        const val LEFT_BASE = 0.2f
        const val RIGHT_BASE = 0.3f
    }
}
