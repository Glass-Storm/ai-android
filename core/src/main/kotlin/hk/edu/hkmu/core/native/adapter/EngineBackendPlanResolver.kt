package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.native.BackendSelector
import hk.edu.hkmu.core.native.DeviceCapabilities
import hk.edu.hkmu.core.native.GracefulBackendSelector

/**
 * Pure mapping from a [BackendSelector] decision to a per-engine [BackendPlan].
 *
 * The selection itself is delegated to the injected [selector], so explicit
 * `Accelerator.Gpu` stays **strict** (a missing GPU raises
 * [hk.edu.hkmu.core.error.AiAndroidException.AcceleratorUnavailable] rather than downgrading),
 * while `Accelerator.Auto` yields the graceful `[Gpu, Cpu]` fallback sequence. This class adds no
 * policy of its own: it only translates each accelerator in the selection into the engine's knob.
 *
 * @param selector the shared decision function; defaults to [GracefulBackendSelector].
 */
class EngineBackendPlanResolver(
    private val selector: BackendSelector = GracefulBackendSelector()
) {
    /**
     * @return a deterministic, ordered plan for [adapter].
     * @throws hk.edu.hkmu.core.error.AiAndroidException.AcceleratorUnavailable when an explicitly
     *   requested accelerator is unavailable (inherited from the selector).
     */
    fun <K : BackendKnob> resolve(
        adapter: EngineBackendAdapter<K>,
        requested: Accelerator,
        capabilities: DeviceCapabilities
    ): BackendPlan<K> {
        val selection = selector.select(requested, capabilities)
        val attempts = selection.fallbackSequence.map { accelerator ->
            BackendAttempt(accelerator, adapter.knobFor(accelerator))
        }
        return BackendPlan(adapter.engineId, attempts)
    }
}
