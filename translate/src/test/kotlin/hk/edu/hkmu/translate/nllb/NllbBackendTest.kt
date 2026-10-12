@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.native.DeviceCapabilities
import hk.edu.hkmu.core.native.adapter.OnnxExecutionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tier A acceptance for the ONNX execution-provider resolution through the shared backend adapter. */
class NllbBackendTest {

    @Test
    fun `cpu stays cpu`() {
        val plan = NllbBackend.plan(Accelerator.Cpu, DeviceCapabilities.CpuOnly)

        assertEquals(listOf(OnnxExecutionProvider.Cpu), plan.attempts.map { it.knob })
    }

    @Test
    fun `auto is cpu only on a cpu device`() {
        val plan = NllbBackend.plan(Accelerator.Auto, DeviceCapabilities.CpuOnly)

        assertEquals(listOf(OnnxExecutionProvider.Cpu), plan.attempts.map { it.knob })
    }

    @Test
    fun `auto prefers nnapi and falls back to cpu when a gpu is available`() {
        val capabilities = DeviceCapabilities(listOf(Accelerator.Cpu, Accelerator.Gpu))

        val plan = NllbBackend.plan(Accelerator.Auto, capabilities)

        assertEquals(listOf(OnnxExecutionProvider.Gpu, OnnxExecutionProvider.Cpu), plan.attempts.map { it.knob })
    }

    @Test
    fun `explicit gpu without a capability is strict`() {
        assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            NllbBackend.plan(Accelerator.Gpu, DeviceCapabilities.CpuOnly)
        }
    }
}
