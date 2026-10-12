package hk.edu.hkmu.core.pipeline

/**
 * The composed pipelines the library defines over the core engine interfaces.
 *
 * Each kind fixes the *source* stage it starts from and the set of stages that are legal for it; the
 * concrete order is supplied separately by a [PipelineSpec] so it stays explicit and configurable.
 *
 * @property sourceStage The stage that must appear first and produce the stream being composed.
 * @property legalStages Every stage this kind may contain, in the canonical (default) order.
 */
enum class PipelineKind(
    val sourceStage: PipelineStage,
    val legalStages: List<PipelineStage>
) {
    /** Speech: `Asr -> (recombination) -> (translation)`. */
    SpeechToText(
        sourceStage = PipelineStage.Asr,
        legalStages = listOf(PipelineStage.Asr, PipelineStage.Recombination, PipelineStage.Translation)
    ),

    /** Sign: `Sign -> (recombination)`. Translation is not a legal sign stage. */
    SignToText(
        sourceStage = PipelineStage.Sign,
        legalStages = listOf(PipelineStage.Sign, PipelineStage.Recombination)
    )
}
