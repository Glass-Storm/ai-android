package hk.edu.hkmu.core.budget

import java.util.concurrent.ConcurrentHashMap

/** Supplies the current process PSS in bytes; the Tier B device harness reads `/proc/self/status`. */
fun interface PssProbe {
    /** Current resident proportional set size, in bytes. */
    fun pssBytes(): Long
}

/**
 * Records the peak PSS observed per feature.
 *
 * Deliberately a plain ledger with no Android or engine reference, so the budget arithmetic is
 * unit-testable on the host JVM; the actual sampling lives in [SamplingPssMonitor]. Thread-safe so the
 * sampler thread can write while the instrumented work runs.
 */
class PeakPssRecorder {
    private val peaks = ConcurrentHashMap<MemoryBudget, Long>()

    /** Keeps the maximum of the recorded and current [sampleBytes] for [budget]. */
    fun record(budget: MemoryBudget, sampleBytes: Long) {
        peaks.merge(budget, sampleBytes) { existing, current -> if (current > existing) current else existing }
    }

    /** Peak recorded for [budget], or null when it was never sampled. */
    fun peak(budget: MemoryBudget): Long? = peaks[budget]

    /** Snapshot of every recorded peak. */
    fun snapshot(): Map<MemoryBudget, Long> = peaks.toMap()
}

/**
 * Samples [probe] around [block] and records the peak for [budget].
 *
 * One sample is taken before the block starts, then the probe is polled every [intervalMillis] until
 * the block returns, and once more after it. That leading sample guarantees a value even for an
 * instantaneous block, which keeps the host-JVM test deterministic.
 *
 * @param probe Source of the current PSS.
 * @param recorder Destination for the observed peak.
 * @param intervalMillis Polling cadence while the block runs.
 */
class SamplingPssMonitor(
    private val probe: PssProbe,
    private val recorder: PeakPssRecorder,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    /** Runs [block] while sampling [probe], recording the peak against [budget]. */
    fun monitor(budget: MemoryBudget, block: () -> Unit) {
        recorder.record(budget, probe.pssBytes())
        val sampler = Thread { poll(budget) }
        sampler.isDaemon = true
        sampler.start()
        try {
            block()
        } finally {
            sampler.interrupt()
            recorder.record(budget, probe.pssBytes())
        }
    }

    private fun poll(budget: MemoryBudget) {
        while (!Thread.currentThread().isInterrupted) {
            recorder.record(budget, probe.pssBytes())
            try {
                Thread.sleep(intervalMillis)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    companion object {
        /** Default polling cadence, in milliseconds. */
        const val DEFAULT_INTERVAL_MILLIS = 50L
    }
}
