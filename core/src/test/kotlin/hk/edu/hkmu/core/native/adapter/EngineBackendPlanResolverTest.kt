package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.native.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Locks the (adapter, selector) -> [BackendPlan] mapping: a probed GPU selects the accelerator knob
 * for every engine, a GPU-less device degrades to the CPU knob, and an explicit `Gpu` request stays
 * strict (typed error, never a silent downgrade).
 */
class EngineBackendPlanResolverTest {

    private val resolver = EngineBackendPlanResolver()
    private val cpuOnly = DeviceCapabilities.CpuOnly
    private val withGpu = DeviceCapabilities(listOf(Accelerator.Cpu, Accelerator.Gpu))

    /** (A) happy path: GPU probe true -> the adapter selects the accelerator knob. */
    @Test
    fun `auto with a gpu selects the accelerator knob for every engine`() {
        val sherpa = resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, withGpu)
        assertEquals(Accelerator.Gpu, sherpa.chosen.accelerator)
        assertEquals(SherpaProvider.Gpu, sherpa.chosen.knob)
        assertEquals("nnapi", sherpa.chosen.knob.token)
        assertEquals(listOf(Accelerator.Gpu, Accelerator.Cpu), sherpa.attempts.map { it.accelerator })

        val onnx = resolver.resolve(OnnxBackendAdapter(), Accelerator.Auto, withGpu)
        assertEquals(OnnxExecutionProvider.Gpu, onnx.chosen.knob)
        assertEquals("NnapiExecutionProvider", onnx.chosen.knob.token)

        val llama = resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Auto, withGpu)
        assertEquals(LlamaCppBackend.Vulkan, llama.chosen.knob)
        assertEquals("vulkan", llama.chosen.knob.token)
    }

    @Test
    fun `auto without a gpu degrades every engine to the cpu knob`() {
        val sherpa = resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, cpuOnly)
        assertEquals(Accelerator.Cpu, sherpa.chosen.accelerator)
        assertEquals(SherpaProvider.Cpu, sherpa.chosen.knob)
        assertEquals(listOf(Accelerator.Cpu), sherpa.attempts.map { it.accelerator })

        val onnx = resolver.resolve(OnnxBackendAdapter(), Accelerator.Auto, cpuOnly)
        assertEquals(OnnxExecutionProvider.Cpu, onnx.chosen.knob)

        val llama = resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Auto, cpuOnly)
        assertEquals(LlamaCppBackend.Cpu, llama.chosen.knob)
    }

    @Test
    fun `explicit cpu pins the cpu knob`() {
        val plan = resolver.resolve(SherpaBackendAdapter(), Accelerator.Cpu, withGpu)

        assertEquals(SherpaProvider.Cpu, plan.chosen.knob)
        assertEquals(listOf(Accelerator.Cpu), plan.attempts.map { it.accelerator })
    }

    @Test
    fun `explicit gpu resolves to the accelerator knob when present`() {
        val plan = resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Gpu, withGpu)

        assertEquals(LlamaCppBackend.Vulkan, plan.chosen.knob)
        assertEquals(listOf(Accelerator.Gpu), plan.attempts.map { it.accelerator })
    }

    /** (A) failure: explicit GPU without a GPU must NOT downgrade, it must raise a typed error. */
    @Test
    fun `explicit gpu without a gpu stays strict for every engine`() {
        assertStrictGpu(SherpaBackendAdapter())
        assertStrictGpu(OnnxBackendAdapter())
        assertStrictGpu(LlamaCppBackendAdapter())
    }

    private fun <K : BackendKnob> assertStrictGpu(adapter: EngineBackendAdapter<K>) {
        val error = assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            resolver.resolve(adapter, Accelerator.Gpu, cpuOnly)
        }

        assertEquals(Accelerator.Gpu, error.requested)
        assertEquals(listOf(Accelerator.Cpu), error.available)
    }
}
