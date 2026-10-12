package hk.edu.hkmu.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the `AsrEngine` Flow contract: ordered final segments that complete, and cancellation that
 * stops the engine with no further emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AsrFlowContractTest {

    @Test
    fun `emits ordered final segments and completes`() = runTest {
        val engine = FakeAsrEngine(
            listOf(segment(0), segment(1), segment(2))
        )

        val collected = engine.transcribe(emptyFlow()).toList()

        assertEquals(listOf("seg-0", "seg-1", "seg-2"), collected.map { it.text })
        assertTrue("every segment is final", collected.all { it.isFinal })
        assertTrue("flow completed", engine.completed)
        assertFalse("flow was not cancelled", engine.cancelled)
    }

    @Test
    fun `cancelling collection stops the fake with no further emissions`() = runTest {
        val engine = FakeAsrEngine(
            segments = (0 until SEGMENT_COUNT).map { segment(it) },
            emissionDelayMillis = EMISSION_DELAY_MS
        )
        val received = mutableListOf<TranscriptionSegment>()

        val job = launch { engine.transcribe(emptyFlow()).collect { received += it } }
        advanceTimeBy(EMISSION_DELAY_MS * SEGMENTS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one segment arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(EMISSION_DELAY_MS * SEGMENT_COUNT)
        runCurrent()

        assertEquals("no emissions after cancellation", atCancel, received.size)
        assertTrue("fake observed the cancellation", engine.cancelled)
        assertFalse("fake never completed", engine.completed)
    }

    private fun segment(index: Int) = TranscriptionSegment(
        text = "seg-$index",
        startTimeMillis = index * SEGMENT_MS,
        endTimeMillis = (index + 1) * SEGMENT_MS
    )

    private companion object {
        const val SEGMENT_MS = 1_000L
        const val EMISSION_DELAY_MS = 100L
        const val SEGMENT_COUNT = 20
        const val SEGMENTS_BEFORE_CANCEL = 3
    }
}
