package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator

/**
 * Per-engine backend adapters mapping the shared [hk.edu.hkmu.core.native.BackendSelector] decision
 * to each engine's concrete knob.
 *
 * Each adapter is a pure, stateless mapping: engine modules (13 sherpa ASR/TTS, 14/15 ONNX, 17
 * llama.cpp) consume the produced knob and translate its [BackendKnob.token] into their native
 * configuration. The adapters themselves never touch an engine, so this package has no engine
 * dependency. All three engines support CPU unconditionally and expose a GPU/accelerator tier that
 * the [BackendInitializer] can always fall back from.
 */

/** Maps an [Accelerator] to a sherpa-onnx [SherpaProvider]. Used by the ASR and TTS engines. */
class SherpaBackendAdapter : EngineBackendAdapter<SherpaProvider> {
    override val engineId: String = "sherpa"
    override val supportedAccelerators: Set<Accelerator> = setOf(Accelerator.Cpu, Accelerator.Gpu)

    override fun knobFor(accelerator: Accelerator): SherpaProvider = when (accelerator) {
        Accelerator.Auto, Accelerator.Cpu -> SherpaProvider.Cpu
        Accelerator.Gpu -> SherpaProvider.Gpu
    }
}

/** Maps an [Accelerator] to an ONNX Runtime [OnnxExecutionProvider]. Used by the translation engine. */
class OnnxBackendAdapter : EngineBackendAdapter<OnnxExecutionProvider> {
    override val engineId: String = "onnx"
    override val supportedAccelerators: Set<Accelerator> = setOf(Accelerator.Cpu, Accelerator.Gpu)

    override fun knobFor(accelerator: Accelerator): OnnxExecutionProvider = when (accelerator) {
        Accelerator.Auto, Accelerator.Cpu -> OnnxExecutionProvider.Cpu
        Accelerator.Gpu -> OnnxExecutionProvider.Gpu
    }
}

/** Maps an [Accelerator] to a llama.cpp [LlamaCppBackend]. Used by the recombination engine. */
class LlamaCppBackendAdapter : EngineBackendAdapter<LlamaCppBackend> {
    override val engineId: String = "llama.cpp"
    override val supportedAccelerators: Set<Accelerator> = setOf(Accelerator.Cpu, Accelerator.Gpu)

    override fun knobFor(accelerator: Accelerator): LlamaCppBackend = when (accelerator) {
        Accelerator.Auto, Accelerator.Cpu -> LlamaCppBackend.Cpu
        Accelerator.Gpu -> LlamaCppBackend.Vulkan
    }
}
