@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A download-budget audit: the default provision set (ASR + TTS-yue + NLLB + recombine) must fit
 * the 1900 MB ceiling, and each artifact is checked against its manifest `sizeBytes` with a ±5%
 * tolerance.
 *
 * The declared sizes below are the plan's default-provision model set. ASR (~234 MB) is from
 * `decisions.md`; the others are the documented engine artifact sizes used to size the budget.
 */
class DownloadBudgetTest {

    private val defaultProvisionSet = listOf(
        DeclaredModelSize("sherpa-onnx-sense-voice-...-int8", 234L * MEGABYTE),
        DeclaredModelSize("vits-cantonese-hf-xiaomaiiwn", 115L * MEGABYTE),
        DeclaredModelSize("nllb-200-distilled-600M-onnx-int8", 600L * MEGABYTE),
        DeclaredModelSize("Qwen2.5-0.5B-Instruct-GGUF-q4_0", 350L * MEGABYTE)
    )

    @Test
    fun `default provision set fits the 1900 MB total download budget`() {
        val audit = DownloadBudget.audit(defaultProvisionSet)

        assertEquals(1299L * MEGABYTE, audit.totalBytes)
        assertTrue("default download must fit the ceiling", audit.withinBudget)
        assertTrue("expected headroom under 1900 MB", audit.headroomBytes > 0L)
    }

    @Test
    fun `an oversized provision set is flagged over budget`() {
        val oversized = defaultProvisionSet + DeclaredModelSize("extra-onnx-mt", 700L * MEGABYTE)

        val audit = DownloadBudget.audit(oversized)

        assertFalse(audit.withinBudget)
        assertEquals(-(audit.totalBytes - audit.budgetBytes), audit.headroomBytes)
    }

    @Test
    fun `per-model tolerance is plus or minus five percent`() {
        val declared = 100L * MEGABYTE

        assertTrue(DownloadBudget.withinTolerance(declared, declared))
        assertTrue("exactly +5% is within tolerance", DownloadBudget.withinTolerance(declared, 105L * MEGABYTE))
        assertTrue("exactly -5% is within tolerance", DownloadBudget.withinTolerance(declared, 95L * MEGABYTE))
        assertFalse("over +5% is rejected", DownloadBudget.withinTolerance(declared, 105_000_001L))
        assertEquals(5.0, DownloadBudget.deviationPercent(declared, 105L * MEGABYTE), 1e-9)
    }

    private companion object {
        const val MEGABYTE = 1_000_000L
    }
}
