package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.native.DeviceCapabilities
import hk.edu.hkmu.core.native.adapter.BackendPlan
import hk.edu.hkmu.core.native.adapter.EngineBackendPlanResolver
import hk.edu.hkmu.core.native.adapter.OnnxBackendAdapter
import hk.edu.hkmu.core.native.adapter.OnnxExecutionProvider

/**
 * Resolves the ONNX Runtime execution provider for the translation engine through the shared backend
 * adapter, so the translation engine honors the same backend policy as every other engine:
 * `Auto` gracefully prefers the accelerator and falls back to CPU, while an explicit `Gpu` is strict
 * and raises `AcceleratorUnavailable` when the capability probe reports no GPU.
 */
internal object NllbBackend {
    private val resolver = EngineBackendPlanResolver()

    /** @return the ordered execution-provider plan for [accelerator] on [capabilities]. */
    fun plan(accelerator: Accelerator, capabilities: DeviceCapabilities): BackendPlan<OnnxExecutionProvider> =
        resolver.resolve(OnnxBackendAdapter(), accelerator, capabilities)
}
