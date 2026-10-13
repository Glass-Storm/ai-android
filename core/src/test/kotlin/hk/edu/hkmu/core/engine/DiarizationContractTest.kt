package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [DiarizationEngine] contract: it is a `fun interface` usable as a lambda, and
 * [DiarizationSegment] carries an ordered span plus a speaker label and its 0-based id.
 */
class DiarizationContractTest {

    @Test
    fun `engine is a fun interface usable as a lambda`() = runTest {
        val engine = DiarizationEngine { emptyFlow() }

        assertTrue(engine.diarize(flowOf(AudioChunk(FloatArray(16)))).toList().isEmpty())
    }

    @Test
    fun `segment carries its span plus the 1-based label and 0-based id`() {
        val segment = DiarizationSegment(
            startTimeMillis = 250L,
            endTimeMillis = 2_000L,
            speakerLabel = "Speaker 2",
            speakerId = 1
        )

        assertEquals(250L, segment.startTimeMillis)
        assertEquals(2_000L, segment.endTimeMillis)
        assertEquals("Speaker 2", segment.speakerLabel)
        assertEquals(1, segment.speakerId)
    }

    @Test
    fun `speaker id defaults to null when a producer does not expose one`() {
        val segment = DiarizationSegment(startTimeMillis = 0L, endTimeMillis = 1L, speakerLabel = "Speaker 1")

        assertNull(segment.speakerId)
    }
}
