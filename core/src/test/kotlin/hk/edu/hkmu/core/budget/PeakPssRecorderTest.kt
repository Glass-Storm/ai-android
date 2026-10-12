@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tier A: peak tracking + the monitor's sampling contract (deterministic with a constant probe). */
class PeakPssRecorderTest {

    @Test
    fun `recorder keeps the maximum sample per feature`() {
        val recorder = PeakPssRecorder()

        recorder.record(MemoryBudget.Asr, 120_000_000L)
        recorder.record(MemoryBudget.Asr, 300_000_000L)
        recorder.record(MemoryBudget.Asr, 250_000_000L)

        assertEquals(300_000_000L, recorder.peak(MemoryBudget.Asr))
        assertNull(recorder.peak(MemoryBudget.Tts))
    }

    @Test
    fun `monitor records the peak observed while the block runs`() {
        val recorder = PeakPssRecorder()
        val monitor = SamplingPssMonitor({ PSS_SAMPLE }, recorder)

        monitor.monitor(MemoryBudget.Tts) { /* instantaneous work */ }

        assertEquals(PSS_SAMPLE, recorder.peak(MemoryBudget.Tts))
    }

    private companion object {
        const val PSS_SAMPLE = 42_000_000L
    }
}
