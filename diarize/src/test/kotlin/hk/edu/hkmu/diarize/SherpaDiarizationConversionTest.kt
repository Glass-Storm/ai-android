@file:Suppress("MagicNumber")

package hk.edu.hkmu.diarize

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host JVM coverage for the two pure conversions behind the native diarizer: sherpa reports turn
 * boundaries as `Float` seconds and speaker indices as raw 0-based `Int`s, while the public contract
 * uses `Long` milliseconds, a 1-based `"Speaker N"` label and a 0-based id.
 */
class SherpaDiarizationConversionTest {

    @Test
    fun `seconds convert to whole milliseconds`() {
        assertEquals(1_500L, secondsToMillis(1.5f))
        assertEquals(0L, secondsToMillis(0f))
        assertEquals(2_250L, secondsToMillis(2.25f))
    }

    @Test
    fun `speaker index maps to a 1-based label`() {
        assertEquals("Speaker 1", speakerLabel(0))
        assertEquals("Speaker 2", speakerLabel(1))
        assertEquals("Speaker 3", speakerLabel(2))
    }

    @Test
    fun `speaker id stays 0-based and matches the label offset`() {
        assertEquals(0, speakerId(0))
        assertEquals(2, speakerId(2))
        assertEquals("Speaker 3", speakerLabel(speakerId(2)))
    }
}
