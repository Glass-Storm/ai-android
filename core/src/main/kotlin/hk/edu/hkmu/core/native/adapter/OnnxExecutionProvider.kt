package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator

/**
 * The ONNX Runtime execution-provider (EP) knob used by the translation engine (todo 15).
 *
 * The engine module registers the EP named by [BackendKnob.token] via
 * `SessionOptions.addNnapi()` / `OrtSession.SessionOptions`; [Cpu] is the ORT default and always
 * usable. [Gpu] selects the Android NNAPI EP, which may dispatch to a GPU or, on some SoCs, an
 * accelerator — it is the **accelerator tier**, not an NPU promise. If NNAPI cannot initialize the
 * [BackendInitializer] re-runs the session on [Cpu].
 */
enum class OnnxExecutionProvider(
    override val accelerator: Accelerator,
    override val token: String,
    override val id: String
) : BackendKnob {
    /** `CPUExecutionProvider` — always available. */
    Cpu(Accelerator.Cpu, "CPUExecutionProvider", "onnx.ep.cpu"),

    /** `NnapiExecutionProvider` — Android accelerator EP; advisory, CPU-fallbackable. */
    Gpu(Accelerator.Gpu, "NnapiExecutionProvider", "onnx.ep.nnapi")
}
