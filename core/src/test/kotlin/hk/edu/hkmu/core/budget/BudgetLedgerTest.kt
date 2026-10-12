@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tier A: the ledger evaluates memory peaks and download sizes against the Budgets table. */
class BudgetLedgerTest {

    private val provisionSet = listOf(DeclaredModelSize("asr", 234L * MEGABYTE))

    @Test
    fun `within-budget measurements pass`() {
        val ledger = BudgetLedger()
        ledger.recordMemory(MemoryBudget.Asr, 350_000_000L)
        ledger.recordDownload(provisionSet)

        val report = ledger.report()

        assertTrue(report.passed)
        assertTrue(report.violations.isEmpty())
        ledger.assertWithinBudget()
    }

    @Test
    fun `a memory peak over budget is reported and throws`() {
        val ledger = BudgetLedger()
        ledger.recordMemory(MemoryBudget.Asr, 401_000_000L)
        ledger.recordMemory(MemoryBudget.Recombination, 100_000_000L)

        val report = ledger.report()

        assertFalse(report.passed)
        assertEquals(listOf("ASR"), report.violations.map { it.subject })
        assertEquals(1_000_000L, report.violations.single().overByBytes)
        assertThrows(BudgetExceededException::class.java) { ledger.assertWithinBudget() }
    }

    @Test
    fun `an over-budget total download is reported`() {
        val ledger = BudgetLedger()
        ledger.recordDownload(listOf(DeclaredModelSize("huge", 2_000L * MEGABYTE)))

        val report = ledger.report()

        assertFalse(report.passed)
        assertEquals("total default download", report.violations.single().subject)
    }

    private companion object {
        const val MEGABYTE = 1_000_000L
    }
}
