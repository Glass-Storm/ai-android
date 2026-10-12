package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Locks the backend-selection contract: `Auto` falls back GPU -> CPU gracefully and deterministically,
 * `Cpu` pins CPU, and an explicit `Gpu` request is strict.
 */
class BackendSelectorTest {

    private val selector = GracefulBackendSelector()
    private val cpuOnly = DeviceCapabilities.CpuOnly
    private val withGpu = DeviceCapabilities(listOf(Accelerator.Cpu, Accelerator.Gpu))

    @Test
    fun `auto with no gpu resolves to cpu`() {
        val selection = selector.select(Accelerator.Auto, cpuOnly)

        assertEquals(Accelerator.Cpu, selection.chosen)
        assertEquals(listOf(Accelerator.Cpu), selection.fallbackSequence)
    }

    @Test
    fun `auto with a gpu prefers gpu and keeps cpu as the fallback`() {
        val selection = selector.select(Accelerator.Auto, withGpu)

        assertEquals(Accelerator.Gpu, selection.chosen)
        assertEquals(listOf(Accelerator.Gpu, Accelerator.Cpu), selection.fallbackSequence)
    }

    @Test
    fun `explicit cpu pins cpu`() {
        val selection = selector.select(Accelerator.Cpu, withGpu)

        assertEquals(Accelerator.Cpu, selection.chosen)
        assertEquals(listOf(Accelerator.Cpu), selection.fallbackSequence)
    }

    @Test
    fun `explicit gpu resolves to gpu when present`() {
        val selection = selector.select(Accelerator.Gpu, withGpu)

        assertEquals(Accelerator.Gpu, selection.chosen)
        assertEquals(listOf(Accelerator.Gpu), selection.fallbackSequence)
    }

    @Test
    fun `explicit gpu without a gpu raises a typed error instead of downgrading`() {
        val error = assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            selector.select(Accelerator.Gpu, cpuOnly)
        }

        assertEquals(Accelerator.Gpu, error.requested)
        assertEquals(listOf(Accelerator.Cpu), error.available)
    }

    @Test
    fun `selection is deterministic across repeated calls`() {
        val autoWithGpu = selector.select(Accelerator.Auto, withGpu)
        repeat(DETERMINISM_RUNS) {
            assertEquals(
                listOf(Accelerator.Gpu, Accelerator.Cpu),
                selector.select(Accelerator.Auto, withGpu).fallbackSequence
            )
            assertEquals(autoWithGpu, selector.select(Accelerator.Auto, withGpu))
            assertEquals(Accelerator.Cpu, selector.select(Accelerator.Auto, cpuOnly).chosen)
        }
    }

    @Test
    fun `probe then select resolves to cpu on a device with no gpu`() {
        val capabilities = CapabilityProbe(
            listOf(object : CapabilityProvider {
                override val accelerator: Accelerator = Accelerator.Gpu
                override fun isAvailable(): Boolean = false
            })
        ).probe()

        val selection = selector.select(Accelerator.Auto, capabilities)

        assertEquals(Accelerator.Cpu, selection.chosen)
        assertEquals(listOf(Accelerator.Cpu), selection.fallbackSequence)
    }

    private companion object {
        const val DETERMINISM_RUNS = 5
    }
}
