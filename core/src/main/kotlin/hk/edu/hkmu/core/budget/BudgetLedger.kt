package hk.edu.hkmu.core.budget

/** A single measured value that exceeded its Budgets-table ceiling. */
data class BudgetViolation(val subject: String, val actualBytes: Long, val budgetBytes: Long) {
    /** Bytes by which [actualBytes] exceeds [budgetBytes]. */
    val overByBytes: Long get() = actualBytes - budgetBytes
}

/** Aggregated budget audit: per-feature memory peaks plus the optional download-size audit. */
data class BudgetReport(
    val memoryPeaks: Map<MemoryBudget, Long>,
    val downloads: DownloadBudgetAudit?
) {
    /** Every measurement that exceeds its ceiling, in a stable order. */
    val violations: List<BudgetViolation> = memoryViolations(memoryPeaks) + downloadViolations(downloads)

    /** True when nothing exceeded its ceiling. */
    val passed: Boolean get() = violations.isEmpty()
}

/**
 * Raised by [BudgetLedger.assertWithinBudget] when one or more recorded measurements exceed a
 * Budgets-table ceiling.
 */
class BudgetExceededException(val violations: List<BudgetViolation>) :
    IllegalStateException(
        "Budgets exceeded: " +
            violations.joinToString { "${it.subject} over by ${it.overByBytes} B (budget ${it.budgetBytes} B)" }
    )

/**
 * Collects peak-PSS samples and a download-size audit, then evaluates them against the plan's
 * Budgets table.
 *
 * Instrumentation-only: it holds no Android or engine reference, so the same ledger runs on the host
 * JVM (Tier A, with declared sizes) and inside the Tier B device harness (with measured sizes and a
 * real [PssProbe]) unchanged.
 */
class BudgetLedger {
    private val recorder = PeakPssRecorder()
    private var downloads: DownloadBudgetAudit? = null

    /** Records a single PSS sample for [budget]. */
    fun recordMemory(budget: MemoryBudget, sampleBytes: Long) {
        recorder.record(budget, sampleBytes)
    }

    /** Runs [block] while sampling [probe], recording the peak for [budget]. */
    fun monitor(budget: MemoryBudget, probe: PssProbe, block: () -> Unit) {
        SamplingPssMonitor(probe, recorder).monitor(budget, block)
    }

    /** Records the provision set whose total download size is audited against the 1900 MB ceiling. */
    fun recordDownload(
        provisionSet: List<DeclaredModelSize>,
        budgetBytes: Long = DownloadBudget.TOTAL_DEFAULT_DOWNLOAD_MAX_BYTES
    ) {
        downloads = DownloadBudget.audit(provisionSet, budgetBytes)
    }

    /** Builds the report without throwing. */
    fun report(): BudgetReport = BudgetReport(recorder.snapshot(), downloads)

    /** @throws BudgetExceededException when any recorded value exceeds its ceiling. */
    fun assertWithinBudget() {
        val report = report()
        if (!report.passed) {
            throw BudgetExceededException(report.violations)
        }
    }
}

private fun memoryViolations(peaks: Map<MemoryBudget, Long>): List<BudgetViolation> =
    MemoryBudget.entries.mapNotNull { budget ->
        val actual = peaks[budget] ?: return@mapNotNull null
        if (actual > budget.maxBytes) BudgetViolation(budget.feature, actual, budget.maxBytes) else null
    }

private fun downloadViolations(downloads: DownloadBudgetAudit?): List<BudgetViolation> =
    if (downloads != null && !downloads.withinBudget) {
        listOf(BudgetViolation(TOTAL_DOWNLOAD_SUBJECT, downloads.totalBytes, downloads.budgetBytes))
    } else {
        emptyList()
    }

private const val TOTAL_DOWNLOAD_SUBJECT = "total default download"
