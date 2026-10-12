package hk.edu.hkmu.core.budget

import kotlin.math.abs

/**
 * A provisioned model's **declared** artifact size — the `sizeBytes` pinned in its signed manifest
 * (or BYO registration). The actual downloaded artifact is compared against this with
 * [DownloadBudget.withinTolerance].
 */
data class DeclaredModelSize(val modelId: String, val sizeBytes: Long)

/** Outcome of auditing a provision set's total download size against the Budgets-table ceiling. */
data class DownloadBudgetAudit(val modelSizes: List<DeclaredModelSize>, val budgetBytes: Long) {
    /** Sum of every declared artifact size, in bytes. */
    val totalBytes: Long get() = modelSizes.sumOf { it.sizeBytes }

    /** True when the total fits the ceiling. */
    val withinBudget: Boolean get() = totalBytes <= budgetBytes

    /** Bytes still available under the ceiling (negative when over). */
    val headroomBytes: Long get() = budgetBytes - totalBytes
}

/**
 * Download-size budgets from the plan's Budgets table: the **total default download**
 * (ASR + TTS-yue + NLLB + recombine) is ≤1900 MB, and each installed artifact must be within ±5% of
 * its manifest `sizeBytes`.
 *
 * The audit is pure arithmetic over declared/measured sizes, so it runs unchanged in the Tier A unit
 * suite (declared sizes) and in the Tier B harness (measured on-disk bytes).
 */
object DownloadBudget {
    /** Total default download ceiling, in decimal megabytes (Budgets table). */
    const val TOTAL_DEFAULT_DOWNLOAD_MAX_MB = 1900L

    /** Allowed deviation of a measured artifact from its manifest `sizeBytes`, in percent. */
    const val PER_MODEL_TOLERANCE_PERCENT = 5.0

    /** [TOTAL_DEFAULT_DOWNLOAD_MAX_MB] expressed in bytes. */
    const val TOTAL_DEFAULT_DOWNLOAD_MAX_BYTES = TOTAL_DEFAULT_DOWNLOAD_MAX_MB * MemoryBudget.MEGABYTE

    /** Audits [provisionSet] against [budgetBytes] (defaults to the 1900 MB ceiling). */
    fun audit(
        provisionSet: List<DeclaredModelSize>,
        budgetBytes: Long = TOTAL_DEFAULT_DOWNLOAD_MAX_BYTES
    ): DownloadBudgetAudit = DownloadBudgetAudit(provisionSet, budgetBytes)

    /** Absolute deviation of [measuredBytes] from [declaredBytes], as a percentage of declared. */
    fun deviationPercent(declaredBytes: Long, measuredBytes: Long): Double {
        require(declaredBytes > 0L) { "declaredBytes must be positive, was $declaredBytes" }
        return abs(measuredBytes - declaredBytes) / declaredBytes.toDouble() * PERCENT
    }

    /** True when [measuredBytes] is within [PER_MODEL_TOLERANCE_PERCENT] of [declaredBytes]. */
    fun withinTolerance(declaredBytes: Long, measuredBytes: Long): Boolean =
        deviationPercent(declaredBytes, measuredBytes) <= PER_MODEL_TOLERANCE_PERCENT

    private const val PERCENT = 100.0
}
