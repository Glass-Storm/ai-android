@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.budget

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the Budgets table and a representative audit for the evidence log.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :core:testDebugUnitTest --tests '*BudgetManualQaProbeTest' -i --rerun-tasks`.
 */
class BudgetManualQaProbeTest {

    @Test
    fun `manual QA - prints the budget table and the default download audit`() {
        println("BUDGET-MANUAL-QA memory budgets (plan's Budgets table):")
        MemoryBudget.entries.forEach { budget ->
            println("BUDGET-MANUAL-QA   ${budget.feature} <= ${budget.maxMegabytes} MB (${budget.maxBytes} B)")
        }

        val ledger = BudgetLedger()
        ledger.recordMemory(MemoryBudget.Asr, 350_000_000L)
        ledger.recordMemory(MemoryBudget.Tts, 180_000_000L)
        ledger.recordMemory(MemoryBudget.Translation, 1_500_000_000L)
        ledger.recordMemory(MemoryBudget.Sign, 200_000_000L)
        ledger.recordMemory(MemoryBudget.Recombination, 550_000_000L)
        ledger.recordMemory(MemoryBudget.WorstCase, 2_050_000_000L)
        ledger.recordDownload(DEFAULT_PROVISION_SET)

        val report = ledger.report()
        report.memoryPeaks.forEach { (budget, peak) ->
            val verdict = if (peak <= budget.maxBytes) "OK" else "OVER"
            println("BUDGET-MANUAL-QA peak ${budget.feature}: $peak B / ${budget.maxBytes} B -> $verdict")
        }
        val downloads = report.downloads
        println(
            "BUDGET-MANUAL-QA download total: ${downloads?.totalBytes} B / " +
                "${downloads?.budgetBytes} B (headroom ${downloads?.headroomBytes} B)"
        )
        println("BUDGET-MANUAL-QA report passed=${report.passed} violations=${report.violations.size}")

        assertTrue("the representative audit fits the table", report.passed)
    }

    private companion object {
        val DEFAULT_PROVISION_SET = listOf(
            DeclaredModelSize("asr-sensevoice-int8", 234L * 1_000_000L),
            DeclaredModelSize("tts-vits-cantonese", 115L * 1_000_000L),
            DeclaredModelSize("nllb-600M-onnx-int8", 600L * 1_000_000L),
            DeclaredModelSize("recombine-qwen2-0_5b-gguf", 350L * 1_000_000L)
        )
    }
}
