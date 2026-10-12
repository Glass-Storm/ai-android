package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator

/**
 * The sherpa-onnx `provider` knob.
 *
 * [Cpu] is the unconditionally safe provider. [Gpu] requests the Android platform accelerator
 * delegate (NNAPI); it may resolve to a GPU or a dedicated accelerator depending on the device
 * driver, so it is treated as the **accelerator tier** and never promises any specific silicon.
 * The engine module (13/14) passes [BackendKnob.token] straight into its sherpa config; when that
 * provider cannot initialize the [BackendInitializer] falls back to [Cpu].
 */
enum class SherpaProvider(
    override val accelerator: Accelerator,
    override val token: String,
    override val id: String
) : BackendKnob {
    /** ONNX Runtime CPU provider — always available. */
    Cpu(Accelerator.Cpu, "cpu", "sherpa.provider.cpu"),

    /** Android accelerator delegate (NNAPI). Advisory; may map to GPU, always has a CPU fallback. */
    Gpu(Accelerator.Gpu, "nnapi", "sherpa.provider.nnapi")
}
