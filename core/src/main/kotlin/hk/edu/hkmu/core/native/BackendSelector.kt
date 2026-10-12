package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * The backend chosen for a run, plus the ordered fallbacks the selector would use.
 *
 * [fallbackSequence] always begins with [chosen] and is deterministic for a given
 * (requested, capabilities) pair.
 */
data class BackendSelection(
    val chosen: Accelerator,
    val fallbackSequence: List<Accelerator>
)

/** Deterministically maps a requested accelerator plus probed capabilities to a concrete backend. */
interface BackendSelector {
    /**
     * @param requested the accelerator the caller asked for; [Accelerator.Auto] means "best effort".
     * @param capabilities the accelerators the device actually has.
     * @return the chosen backend and its fallback sequence.
     * @throws AiAndroidException.AcceleratorUnavailable when [Accelerator.Gpu] is requested
     *   explicitly and no GPU is available — an explicit request never silently downgrades.
     */
    fun select(requested: Accelerator, capabilities: DeviceCapabilities): BackendSelection
}

/**
 * Graceful, deterministic fallback selector.
 *
 * - [Accelerator.Auto] prefers GPU then CPU; the fallback sequence is `[Gpu, Cpu]` when a GPU is
 *   present, otherwise `[Cpu]`.
 * - [Accelerator.Cpu] pins CPU (`[Cpu]`).
 * - [Accelerator.Gpu] is **strict**: it resolves to `[Gpu]` when a GPU is available, and otherwise
 *   raises [AiAndroidException.AcceleratorUnavailable] rather than downgrading, matching the
 *   [Accelerator] contract.
 */
class GracefulBackendSelector : BackendSelector {
    override fun select(requested: Accelerator, capabilities: DeviceCapabilities): BackendSelection {
        return when (requested) {
            Accelerator.Cpu -> pinCpu()
            Accelerator.Gpu -> selectGpu(capabilities)
            Accelerator.Auto -> selectAuto(capabilities)
        }
    }

    private fun pinCpu(): BackendSelection = BackendSelection(Accelerator.Cpu, listOf(Accelerator.Cpu))

    private fun selectGpu(capabilities: DeviceCapabilities): BackendSelection {
        if (!capabilities.supports(Accelerator.Gpu)) {
            throw AiAndroidException.AcceleratorUnavailable(Accelerator.Gpu, capabilities.accelerators)
        }
        return BackendSelection(Accelerator.Gpu, listOf(Accelerator.Gpu))
    }

    private fun selectAuto(capabilities: DeviceCapabilities): BackendSelection {
        val sequence = buildList {
            if (capabilities.supports(Accelerator.Gpu)) add(Accelerator.Gpu)
            add(Accelerator.Cpu)
        }
        return BackendSelection(sequence.first(), sequence)
    }
}
