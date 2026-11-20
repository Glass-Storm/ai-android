package hk.edu.hkmu.sign.shuwa

import android.graphics.Bitmap
import hk.edu.hkmu.sign.landmark.Keypoint3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Host-JVM tests for the landmarker facade. The native holistic graph cannot run here, so a fake
 * backend stands in; this pins the pass-through, the timestamp and the close contract.
 */
@RunWith(RobolectricTestRunner::class)
class ShuwaLandmarkerTest {

    @Test
    fun `detect returns the backend frame unchanged`() {
        val frame = sampleFrame(timestampMillis = 1_234L)
        val landmarker = ShuwaLandmarker.forTesting(FakeBackend(frame))

        val result = landmarker.detect(bitmap(), 1_234L)

        assertSame(frame, result)
        assertEquals(1_234L, result.timestampMillis)
    }

    @Test
    fun `close releases the backend exactly once when idempotent`() {
        val backend = FakeBackend(sampleFrame(0L))
        val landmarker = ShuwaLandmarker.forTesting(backend)

        landmarker.detect(bitmap(), 0L)
        landmarker.close()
        landmarker.close()

        assertTrue(backend.closed)
    }

    @Test
    fun `an empty detection is representable`() {
        val empty =
            ShuwaFrame(
                pose = emptyList(),
                face = emptyList(),
                leftHand = emptyList(),
                rightHand = emptyList(),
                timestampMillis = 0L
            )
        val landmarker = ShuwaLandmarker.forTesting(FakeBackend(empty))

        val result = landmarker.detect(bitmap(), 0L)

        assertTrue(result.pose.isEmpty())
        assertFalse(result.timestampMillis != 0L)
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private fun sampleFrame(timestampMillis: Long): ShuwaFrame = ShuwaFrame(
        pose = listOf(ShuwaPosePoint(0.5f, 0.5f, 0f, 0.9f)),
        face = listOf(Keypoint3D(0.5f, 0.5f, 0f)),
        leftHand = listOf(Keypoint3D(0.4f, 0.4f, 0f)),
        rightHand = listOf(Keypoint3D(0.6f, 0.6f, 0f)),
        timestampMillis = timestampMillis
    )

    private class FakeBackend(private val frame: ShuwaFrame) : ShuwaLandmarkerBackend {
        var closed: Boolean = false
            private set

        override fun detect(image: Bitmap, timestampMillis: Long): ShuwaFrame = frame

        override fun close() {
            closed = true
        }
    }
}
