@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignSoftmaxTest {

    @Test
    fun `softmax is a normalized distribution`() {
        val probabilities = SignSoftmax.softmax(floatArrayOf(1f, 2f, 3f))

        assertEquals(1.0, probabilities.sum().toDouble(), 1e-6)
        assertTrue(probabilities.all { it in 0f..1f })
        assertEquals(2, SignSoftmax.argMax(probabilities))
    }

    @Test
    fun `softmax is stable for large logits`() {
        val probabilities = SignSoftmax.softmax(floatArrayOf(1000f, 1001f, 1002f))

        assertTrue(probabilities.all { it.isFinite() })
        assertEquals(1.0, probabilities.sum().toDouble(), 1e-6)
        assertEquals(2, SignSoftmax.argMax(probabilities))
    }

    @Test
    fun `arg max keeps the lowest index on ties`() {
        assertEquals(0, SignSoftmax.argMax(floatArrayOf(5f, 5f, 1f)))
    }

    @Test
    fun `empty input yields an empty distribution`() {
        assertEquals(0, SignSoftmax.softmax(FloatArray(0)).size)
        assertEquals(0, SignSoftmax.argMax(FloatArray(0)))
    }
}
