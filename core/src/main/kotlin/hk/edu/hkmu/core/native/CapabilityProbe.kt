package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.config.Accelerator

/**
 * Reports whether a non-CPU [accelerator] is usable on the current device.
 *
 * Providers are queried in registration order; the probe never assumes support it has not been told
 * about. NPU is intentionally not modelled — v1 makes no NPU promise.
 */
interface CapabilityProvider {
    /** The accelerator this provider reports on. */
    val accelerator: Accelerator

    /** @return true when [accelerator] can actually be used on this device. */
    fun isAvailable(): Boolean
}

/**
 * The accelerators the current device can use.
 *
 * [Accelerator.Cpu] is always available; optional accelerators appear only when a
 * [CapabilityProvider] reports support. The list order is deterministic (CPU first, then providers
 * in registration order).
 */
data class DeviceCapabilities(
    val accelerators: List<Accelerator>
) {
    /** True when a GPU provider reported support. */
    val supportsGpu: Boolean get() = supports(Accelerator.Gpu)

    /** @return true when [accelerator] is in the probed set. */
    fun supports(accelerator: Accelerator): Boolean = accelerator in accelerators

    companion object {
        /** The minimal, always-valid capability set: CPU only. */
        val CpuOnly: DeviceCapabilities = DeviceCapabilities(listOf(Accelerator.Cpu))
    }
}

/**
 * Probes device accelerators.
 *
 * CPU is unconditional. Every other accelerator is added only when its [CapabilityProvider]
 * reports support, so a device with no GPU provider (or a provider returning false) yields CPU
 * only.
 *
 * @param providers the optional-accelerator probes to consult.
 */
class CapabilityProbe(
    private val providers: List<CapabilityProvider> = emptyList()
) {
    /** @return the probed [DeviceCapabilities]; CPU is always present. */
    fun probe(): DeviceCapabilities {
        val available = LinkedHashSet<Accelerator>()
        available += Accelerator.Cpu
        providers.forEach { provider ->
            if (provider.accelerator != Accelerator.Cpu && provider.isAvailable()) {
                available += provider.accelerator
            }
        }
        return DeviceCapabilities(available.toList())
    }
}
