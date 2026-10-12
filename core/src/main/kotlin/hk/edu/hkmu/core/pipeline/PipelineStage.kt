package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.di.EngineSlot

/**
 * A single processing stage the composition layer can place in a pipeline.
 *
 * A stage is the *slot* — the [hk.edu.hkmu.core.engine] interface it is filled by — not a concrete
 * implementation. The composition layer reasons only about these stages, so a host can swap any real
 * engine for a fake without the ordering contract changing.
 *
 * @property engineName Public interface name of the engine that fills this stage (shared with the DI
 *   [EngineSlot] so a missing engine is reported by a stable, human-meaningful name).
 */
enum class PipelineStage(val engineName: String) {
    /** Speech-to-text: consumes audio, emits [hk.edu.hkmu.core.engine.TranscriptionSegment]s. */
    Asr(EngineSlot.Asr.engineName),

    /** Sign-language recognition: consumes landmark frames, emits predictions at window boundaries. */
    Sign(EngineSlot.Sign.engineName),

    /** LLM recombination / post-processing: consumes text, streams tokens. Default-ON but lazy. */
    Recombination(EngineSlot.PostProcess.engineName),

    /** Text translation: consumes text, emits translated text. */
    Translation(EngineSlot.Translation.engineName)
}
