package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.config.Accelerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the capability-probe contract: CPU is always reported and GPU only when a provider says so.
 * NPU is never promised.
 */
class CapabilityProbeTest {

    @Test
    fun `cpu is always available with no providers`() {
        val capabilities = CapabilityProbe().probe()

        assertEquals(listOf(Accelerator.Cpu), capabilities.accelerators)
        assertTrue(capabilities.supports(Accelerator.Cpu))
        assertFalse(capabilities.supportsGpu)
    }

    @Test
    fun `gpu appears only when a provider reports support`() {
        val withGpu = CapabilityProbe(listOf(FakeGpuProvider(available = true))).probe()
        assertTrue(withGpu.supportsGpu)
        assertEquals(listOf(Accelerator.Cpu, Accelerator.Gpu), withGpu.accelerators)

        val withoutGpu = CapabilityProbe(listOf(FakeGpuProvider(available = false))).probe()
        assertFalse(withoutGpu.supportsGpu)
        assertEquals(listOf(Accelerator.Cpu), withoutGpu.accelerators)
    }

    @Test
    fun `a provider claiming cpu does not duplicate the cpu entry`() {
        val capabilities = CapabilityProbe(listOf(FakeCpuProvider(available = true))).probe()

        assertEquals(listOf(Accelerator.Cpu), capabilities.accelerators)
    }

    @Test
    fun `probe result is deterministic across repeated calls`() {
        val probe = CapabilityProbe(listOf(FakeGpuProvider(available = true)))

        assertEquals(probe.probe(), probe.probe())
        assertEquals(probe.probe().accelerators, probe.probe().accelerators)
    }

    @Test
    fun `the probed set only ever contains cpu or gpu`() {
        val capabilities = CapabilityProbe(
            listOf(FakeGpuProvider(available = true), FakeCpuProvider(available = false))
        ).probe()

        assertTrue(capabilities.accelerators.all { it == Accelerator.Cpu || it == Accelerator.Gpu })
    }

    private class FakeGpuProvider(private val available: Boolean) : CapabilityProvider {
        override val accelerator: Accelerator = Accelerator.Gpu
        override fun isAvailable(): Boolean = available
    }

    private class FakeCpuProvider(private val available: Boolean) : CapabilityProvider {
        override val accelerator: Accelerator = Accelerator.Cpu
        override fun isAvailable(): Boolean = available
    }
}
