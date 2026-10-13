@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.gesture

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier A: [MediaPipeGestureRecognizer.recognize] maps a [GestureBackend] result onto the public
 * [GesturePrediction] and reports `null` when there is no gesture. The backend is a fake, so no
 * MediaPipe native is touched; Robolectric supplies a real [Bitmap].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GesturePredictionMappingTest {

    @Test
    fun `recognize maps the fake backend result to a prediction`() {
        val backend = FakeGestureBackend(GestureBackendResult("Thumb_Up", 0.93f, "Right", 1))

        MediaPipeGestureRecognizer(backend).use { recognizer ->
            val prediction = recognizer.recognize(bitmap())

            assertEquals("Thumb_Up", prediction?.label)
            assertEquals(0.93f, prediction?.score ?: 0f, TOLERANCE)
            assertEquals("Right", prediction?.handedness)
            assertEquals(1, prediction?.handCount)
        }
    }

    @Test
    fun `recognize returns null when the backend reports no gesture`() {
        val backend = FakeGestureBackend(GestureBackendResult("", 0f, null, 0))

        MediaPipeGestureRecognizer(backend).use { recognizer ->
            assertNull(recognizer.recognize(bitmap()))
        }
    }

    @Test
    fun `recognize returns null when a hand is present but no gesture label`() {
        val backend = FakeGestureBackend(GestureBackendResult("", 0f, "Left", 1))

        MediaPipeGestureRecognizer(backend).use { recognizer ->
            assertNull(recognizer.recognize(bitmap()))
        }
    }

    @Test
    fun `recognize forwards every frame to the backend`() {
        val backend = FakeGestureBackend(GestureBackendResult("Victory", 0.8f, null, 1))
        val recognizer = MediaPipeGestureRecognizer(backend)

        recognizer.recognize(bitmap())
        recognizer.recognize(bitmap())

        assertEquals(2, backend.recognizeCount)
        recognizer.close()
    }

    @Test
    fun `close is idempotent`() {
        val backend = FakeGestureBackend(GestureBackendResult("Victory", 0.8f, null, 1))
        val recognizer = MediaPipeGestureRecognizer(backend)

        recognizer.recognize(bitmap())
        recognizer.close()
        recognizer.close()

        assertEquals(1, backend.recognizeCount)
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
