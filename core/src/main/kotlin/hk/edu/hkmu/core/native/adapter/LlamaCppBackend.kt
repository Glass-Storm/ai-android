package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator

/**
 * The ggml backend knob used by the recombination LLM (todo 17).
 *
 * [Cpu] runs the pure-CPU ggml backend built by default. [Vulkan] requests the Vulkan ggml backend;
 * it is the **accelerator tier** and is only valid when the native build was compiled with
 * `GGML_VULKAN=ON`. When the Vulkan device/instance fails to initialize the [BackendInitializer]
 * drops back to [Cpu], so a Vulkan-less build is never forced onto a failing GPU path.
 */
enum class LlamaCppBackend(
    override val accelerator: Accelerator,
    override val token: String,
    override val id: String
) : BackendKnob {
    /** Pure-CPU ggml backend — always available. */
    Cpu(Accelerator.Cpu, "cpu", "llama.cpp.backend.cpu"),

    /** Vulkan ggml backend. Advisory; requires `GGML_VULKAN`; always has a CPU fallback. */
    Vulkan(Accelerator.Gpu, "vulkan", "llama.cpp.backend.vulkan")
}
