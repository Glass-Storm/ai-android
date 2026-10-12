@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.budget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the plan's Budgets table. Each figure is asserted explicitly so a spike (or a
 * careless edit) that **loosens** a budget fails here; the plan permits tightening only.
 */
class MemoryBudgetTest {

    @Test
    fun `memory budgets match the plan's Budgets table`() {
        val expected = mapOf(
            MemoryBudget.Asr to 400L,
            MemoryBudget.Tts to 250L,
            MemoryBudget.Translation to 1600L,
            MemoryBudget.Sign to 250L,
            MemoryBudget.Recombination to 600L,
            MemoryBudget.WorstCase to 2200L
        )

        expected.forEach { (budget, megabytes) ->
            assertEquals("budget for ${budget.feature}", megabytes, budget.maxMegabytes)
        }
    }

    @Test
    fun `maxBytes converts decimal megabytes`() {
        MemoryBudget.entries.forEach { budget ->
            assertEquals(budget.maxMegabytes * 1_000_000L, budget.maxBytes)
        }
        assertEquals(400_000_000L, MemoryBudget.Asr.maxBytes)
        assertEquals(2_200_000_000L, MemoryBudget.WorstCase.maxBytes)
    }
}
