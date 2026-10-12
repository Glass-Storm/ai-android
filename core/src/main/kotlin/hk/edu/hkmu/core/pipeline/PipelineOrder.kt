package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Owns the ordering rules for composed pipelines.
 *
 * The composition layer — not the engines — decides where each stage sits. A [PipelineSpec] stores an
 * explicit, caller-supplied order; this object is the single authority that decides whether that order
 * is legal and, with the recombination opt-out applied, which stages actually run. Any illegal order
 * fails with [AiAndroidException.PipelineMisconfigured] **before** an engine is ever invoked, so a
 * post-processor placed ahead of its source stage can never silently run out of order.
 */
object PipelineOrder {

    /**
     * Validates [stages] for [kind].
     *
     * @throws AiAndroidException.PipelineMisconfigured when the order is empty, contains a stage that
     *   is illegal for [kind], repeats a stage, or does not start from [PipelineKind.sourceStage].
     */
    fun validate(kind: PipelineKind, stages: List<PipelineStage>) {
        failureReason(kind, stages)?.let { throw AiAndroidException.PipelineMisconfigured(it) }
    }

    /**
     * Resolves [stages] for [kind] into the concrete stages that run.
     *
     * When [recombinationEnabled] is false the [PipelineStage.Recombination] stage is dropped from the
     * effective text stages (the opt-out). Recombination is default-ON but lazy: it appears in the
     * default specs yet is only reached per emitted segment and only when enabled.
     */
    fun resolve(kind: PipelineKind, stages: List<PipelineStage>, recombinationEnabled: Boolean): ResolvedPipeline {
        validate(kind, stages)
        val textStages = stages.drop(1).filter { recombinationEnabled || it != PipelineStage.Recombination }
        return ResolvedPipeline(kind, stages, stages.first(), textStages)
    }

    private fun failureReason(kind: PipelineKind, stages: List<PipelineStage>): String? {
        val structural = structuralFailure(kind, stages)
        if (structural != null) {
            return structural
        }
        return orderFailure(kind, stages)
    }

    private fun structuralFailure(kind: PipelineKind, stages: List<PipelineStage>): String? = when {
        stages.isEmpty() -> "no stages configured for ${kind.name}"
        stages.any { it !in kind.legalStages } -> stageNotLegal(kind, stages.first { it !in kind.legalStages })
        stages.distinct().size != stages.size ->
            "duplicate stage(s) ${duplicateNames(stages)} configured for ${kind.name}"
        else -> null
    }

    private fun orderFailure(kind: PipelineKind, stages: List<PipelineStage>): String? =
        if (stages.first() != kind.sourceStage) {
            "${kind.sourceStage.name} must be the first stage of ${kind.name}; a post-processor or " +
                "translator placed before the source stage is misconfigured (order=${stageNames(stages)})"
        } else {
            null
        }

    private fun stageNotLegal(kind: PipelineKind, stage: PipelineStage): String =
        "stage ${stage.name} is not valid for ${kind.name}; allowed=${kind.legalStages.map { it.name }}"

    private fun duplicateNames(stages: List<PipelineStage>): List<String> =
        stages.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sortedBy { it.ordinal }.map { it.name }

    private fun stageNames(stages: List<PipelineStage>): List<String> = stages.map { it.name }
}

/**
 * A [PipelineSpec] resolved against the recombination opt-out: the declared order plus the source and
 * ordered text stages that actually execute.
 *
 * @property kind Pipeline this plan belongs to.
 * @property stages Declared order exactly as configured (source first).
 * @property source The first stage, which produces the stream the text stages transform.
 * @property textStages Stages after [source], in declared order, with recombination removed when the
 *   opt-out was applied.
 */
data class ResolvedPipeline(
    val kind: PipelineKind,
    val stages: List<PipelineStage>,
    val source: PipelineStage,
    val textStages: List<PipelineStage>
)

/**
 * Reports the first [PipelineStage] whose engine was not wired, or null when the wiring is complete.
 *
 * Shared by both composed pipelines so a stage declared in a spec but given no engine fails fast with
 * the typed misconfiguration instead of a null-pointer at collection time.
 */
internal fun missingEngineFailure(
    textStages: List<PipelineStage>,
    hasTranslation: Boolean,
    hasPostProcessor: Boolean
): String? = when {
    PipelineStage.Recombination in textStages && !hasPostProcessor ->
        "stage ${PipelineStage.Recombination.name} requires a ${PipelineStage.Recombination.engineName}"
    PipelineStage.Translation in textStages && !hasTranslation ->
        "stage ${PipelineStage.Translation.name} requires a ${PipelineStage.Translation.engineName}"
    else -> null
}
