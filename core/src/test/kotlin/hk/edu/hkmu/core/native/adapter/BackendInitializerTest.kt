package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.native.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the deterministic fallback contract per engine: a GPU-unavailable accelerator init falls
 * back to CPU, a raw accelerator init failure is surfaced as a typed `AcceleratorUnavailable`
 * (with the original preserved) before falling back, and a CPU failure is terminal.
 */
class BackendInitializerTest {

    private val initializer = BackendInitializer()
    private val resolver = EngineBackendPlanResolver()
    private val withGpu = DeviceCapabilities(listOf(Accelerator.Cpu, Accelerator.Gpu))

    @Test
    fun `sherpa - a typed accelerator failure falls back to the cpu knob`() {
        assertTypedFallback(
            resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, withGpu),
            SherpaProvider.Gpu,
            SherpaProvider.Cpu
        )
    }

    @Test
    fun `onnx - a typed accelerator failure falls back to the cpu knob`() {
        assertTypedFallback(
            resolver.resolve(OnnxBackendAdapter(), Accelerator.Auto, withGpu),
            OnnxExecutionProvider.Gpu,
            OnnxExecutionProvider.Cpu
        )
    }

    @Test
    fun `llama cpp - a typed accelerator failure falls back to the cpu knob`() {
        assertTypedFallback(
            resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Auto, withGpu),
            LlamaCppBackend.Vulkan,
            LlamaCppBackend.Cpu
        )
    }

    /** (A) adversarial: a raw accelerator init throw becomes a typed error, then CPU is selected. */
    @Test
    fun `sherpa - a raw accelerator init throw becomes a typed error then cpu`() {
        assertAdversarialFallback(
            resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, withGpu),
            SherpaProvider.Cpu
        )
    }

    @Test
    fun `onnx - a raw accelerator init throw becomes a typed error then cpu`() {
        assertAdversarialFallback(
            resolver.resolve(OnnxBackendAdapter(), Accelerator.Auto, withGpu),
            OnnxExecutionProvider.Cpu
        )
    }

    @Test
    fun `llama cpp - a raw accelerator init throw becomes a typed error then cpu`() {
        assertAdversarialFallback(
            resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Auto, withGpu),
            LlamaCppBackend.Cpu
        )
    }

    @Test
    fun `happy path - gpu binds and there are no fallbacks`() {
        val plan = resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, withGpu)
        val binder = RecordingBinder<SherpaProvider> { null }

        val result = initializer.initialize(plan, binder.bind)

        assertEquals(Accelerator.Gpu, result.accelerator)
        assertEquals(SherpaProvider.Gpu, result.knob)
        assertTrue(result.fallbacks.isEmpty())
    }

    @Test
    fun `cpu failure propagates unchanged`() {
        val plan = resolver.resolve(OnnxBackendAdapter(), Accelerator.Cpu, withGpu)
        val failure = IllegalStateException("cpu init failed")
        val binder = RecordingBinder<OnnxExecutionProvider> { failure }

        val thrown = assertThrows(IllegalStateException::class.java) {
            initializer.initialize(plan, binder.bind)
        }

        assertSame(failure, thrown)
    }

    @Test
    fun `strict gpu plan surfaces a typed error when the accelerator init fails`() {
        val plan = resolver.resolve(LlamaCppBackendAdapter(), Accelerator.Gpu, withGpu)
        val binder = RecordingBinder<LlamaCppBackend> { IllegalStateException("vulkan instance failed") }

        val error = assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            initializer.initialize(plan, binder.bind)
        }

        assertEquals(Accelerator.Gpu, error.requested)
        assertTrue(error.available.isEmpty())
    }

    @Test
    fun `fallback is deterministic across repeated runs`() {
        repeat(DETERMINISM_RUNS) {
            assertTypedFallback(
                resolver.resolve(SherpaBackendAdapter(), Accelerator.Auto, withGpu),
                SherpaProvider.Gpu,
                SherpaProvider.Cpu
            )
        }
    }

    private fun <K : BackendKnob> assertTypedFallback(plan: BackendPlan<K>, gpuKnob: K, cpuKnob: K) {
        val binder = RecordingBinder<K> { knob ->
            if (knob.accelerator == Accelerator.Gpu) unavailableGpu() else null
        }

        val result = initializer.initialize(plan, binder.bind)

        assertEquals(Accelerator.Cpu, result.accelerator)
        assertEquals(cpuKnob, result.knob)
        assertEquals(listOf(gpuKnob, cpuKnob), binder.attempted)
        assertEquals(Accelerator.Gpu, result.fallbacks.single().requested)
        assertEquals(listOf(Accelerator.Cpu), result.fallbacks.single().available)
    }

    private fun <K : BackendKnob> assertAdversarialFallback(plan: BackendPlan<K>, cpuKnob: K) {
        val failure = IllegalStateException("accelerator device init failed")
        val binder = RecordingBinder<K> { knob ->
            if (knob.accelerator == Accelerator.Gpu) failure else null
        }

        val result = initializer.initialize(plan, binder.bind)

        assertEquals(Accelerator.Cpu, result.accelerator)
        assertEquals(cpuKnob, result.knob)
        val typed = result.fallbacks.single()
        assertEquals(Accelerator.Gpu, typed.requested)
        assertEquals(listOf(Accelerator.Cpu), typed.available)
        assertSame(failure, typed.suppressed.single())
    }

    private fun unavailableGpu(): AiAndroidException.AcceleratorUnavailable =
        AiAndroidException.AcceleratorUnavailable(Accelerator.Gpu, listOf(Accelerator.Cpu))

    /** Records the knobs it was asked to bind and fails per [failWith], emulating a native engine. */
    private class RecordingBinder<K : BackendKnob>(
        private val failWith: (K) -> Throwable?
    ) {
        val attempted = mutableListOf<K>()
        val bind: (K) -> Unit = { knob ->
            attempted += knob
            failWith(knob)?.let { throw it }
        }
    }

    private companion object {
        const val DETERMINISM_RUNS = 5
    }
}
