@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.tts.pcm.toInt16
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16Test {

    @Test
    fun convertsBoundariesAndClipsOutOfRange() {
        val output = toInt16(floatArrayOf(0.0f, 1.0f, -1.0f, 2.0f, -2.0f))

        assertEquals(0, output[0].toInt())
        assertEquals(32_767, output[1].toInt())
        assertEquals(-32_767, output[2].toInt())
        assertEquals(32_767, output[3].toInt())
        assertEquals(-32_767, output[4].toInt())
    }

    @Test
    fun roundsToNearestSample() {
        val output = toInt16(floatArrayOf(0.25f, -0.25f))

        assertEquals(8_192, output[0].toInt())
        assertEquals(-8_192, output[1].toInt())
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertArrayEquals(ShortArray(0), toInt16(FloatArray(0)))
    }

    @Test
    fun audioChunkConversionPreservesRateAndSpeaker() {
        val chunk = AudioChunk(floatArrayOf(0.0f, 1.0f), sampleRateHz = 24_000)

        val pcm = chunk.toInt16(speakerId = 4)

        assertEquals(24_000, pcm.sampleRateHz)
        assertEquals(4, pcm.speakerId)
        assertEquals(2, pcm.samples.size)
        assertEquals(32_767, pcm.samples[1].toInt())
    }
}
