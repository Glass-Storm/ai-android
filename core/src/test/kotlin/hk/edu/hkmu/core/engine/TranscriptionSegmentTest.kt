package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the additive diarization fields on [TranscriptionSegment]: they default to `null` so existing
 * call sites stay source-compatible. Adding fields to the data-class constructor changes the JVM
 * constructor descriptor, so this is **not** binary-compatible (verified via `javap`).
 */
class TranscriptionSegmentTest {

    @Test
    fun `speaker fields default to null`() {
        val segment = TranscriptionSegment(
            text = "hello",
            startTimeMillis = 0L,
            endTimeMillis = 1_000L
        )

        assertNull(segment.speakerLabel)
        assertNull(segment.speakerId)
    }

    @Test
    fun `speaker fields are carried when set`() {
        val segment = TranscriptionSegment(
            text = "hello",
            startTimeMillis = 0L,
            endTimeMillis = 1_000L,
            language = Language.English,
            speakerLabel = "Speaker 1",
            speakerId = 0
        )

        assertEquals("Speaker 1", segment.speakerLabel)
        assertEquals(0, segment.speakerId)
    }

    @Test
    fun `copy without speaker arguments clears both fields`() {
        val attributed = TranscriptionSegment(
            text = "hello",
            startTimeMillis = 0L,
            endTimeMillis = 1_000L,
            speakerLabel = "Speaker 2",
            speakerId = 1
        )

        val cleared = attributed.copy(speakerLabel = null, speakerId = null)

        assertNull(cleared.speakerLabel)
        assertNull(cleared.speakerId)
        assertEquals(attributed.text, cleared.text)
    }
}
