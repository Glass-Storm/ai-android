package hk.edu.hkmu.core.pipeline

/**
 * An explicit, ordered, validated description of the stages a composed pipeline runs.
 *
 * The spec is the composition contract: it states *which* stages run and *in what order*, entirely
 * independently of any concrete engine. It is validated on construction, so an illegal order is
 * rejected with [hk.edu.hkmu.core.error.AiAndroidException.PipelineMisconfigured] the moment it is
 * written — never at stream-collection time. Recombination is part of the default specs (default-ON)
 * but is applied lazily and can be opted out through [resolve].
 *
 * @property kind Pipeline this spec composes.
 * @property stages Declared order of stages (source first). The list is copied on construction.
 */
data class PipelineSpec(
    val kind: PipelineKind,
    val stages: List<PipelineStage>
) {
    init {
        PipelineOrder.validate(kind, stages)
    }

    /** True when [stage] is declared in this spec (ignoring the recombination opt-out). */
    fun contains(stage: PipelineStage): Boolean = stage in stages

    /** Resolves this spec, applying the recombination opt-out when [recombinationEnabled] is false. */
    fun resolve(recombinationEnabled: Boolean = true): ResolvedPipeline =
        PipelineOrder.resolve(kind, stages, recombinationEnabled)

    companion object {
        /** Default speech pipeline: `Asr -> recombination (default-ON, lazy) -> translation`. */
        val SpeechToText: PipelineSpec = PipelineSpec(
            kind = PipelineKind.SpeechToText,
            stages = PipelineKind.SpeechToText.legalStages
        )

        /** Default sign pipeline: `Sign -> recombination (default-ON, lazy)`. */
        val SignToText: PipelineSpec = PipelineSpec(
            kind = PipelineKind.SignToText,
            stages = PipelineKind.SignToText.legalStages
        )

        /**
         * Builds a spec for [kind] with an explicit [stages] order and validates it immediately.
         *
         * @throws hk.edu.hkmu.core.error.AiAndroidException.PipelineMisconfigured when [stages] is not
         *   a legal order for [kind] (e.g. a post-processor before the source stage).
         */
        @JvmStatic
        fun of(kind: PipelineKind, stages: List<PipelineStage>): PipelineSpec = PipelineSpec(kind, stages.toList())
    }
}
