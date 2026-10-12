package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/**
 * Composed sign→text pipeline over the core engine interfaces.
 *
 * ### Composition (owned here, never by an engine)
 * ```
 * Flow<SignFrame> -> Sign -> (Recombination) -> Flow<SignToTextResult>
 * ```
 * The order is exactly the order declared by [spec]. Recombination is **default-ON but lazy**: it runs
 * per emitted window and only when [recombinationEnabled] is true, so the opt-out never touches the
 * (lazily loaded) LLM. Translation is not a legal sign stage and is rejected as a misconfiguration.
 *
 * ### Ceiling
 * This composition recombines *isolated* recognitions; it does **not** perform continuous sign-language
 * sentence translation.
 *
 * @property spec Explicit stage order (source first).
 * @param sign Sign-language engine (required).
 * @param postProcessor Recombination post-processor; required only when recombination is enabled and
 *   declared.
 * @param recombinationEnabled Recombination opt-out; when false the stage is skipped lazily.
 */
class SignToTextPipeline(
    val spec: PipelineSpec,
    private val sign: SignEngine,
    private val postProcessor: PostProcessor? = null,
    private val recombinationEnabled: Boolean = true
) {

    private val plan: ResolvedPipeline = spec.resolve(recombinationEnabled)

    /** Declared stage order exactly as configured (source first). */
    val stages: List<PipelineStage> = spec.stages

    /** Stages that actually execute, in order, after the recombination opt-out is applied. */
    val effectiveStages: List<PipelineStage> = listOf(plan.source) + plan.textStages

    init {
        val failure = if (plan.kind != PipelineKind.SignToText) {
            "SignToTextPipeline requires a ${PipelineKind.SignToText.name} spec, got ${plan.kind.name}"
        } else {
            missingEngineFailure(plan.textStages, hasTranslation = false, hasPostProcessor = postProcessor != null)
        }
        if (failure != null) {
            throw AiAndroidException.PipelineMisconfigured(failure)
        }
    }

    /** Recognizes signs from [frames] and runs every configured text stage in order. */
    fun recognize(frames: Flow<SignFrame>): Flow<SignToTextResult> = flow {
        sign.recognize(frames).collect { prediction ->
            var text = prediction.label
            var recombined: String? = null
            for (stage in plan.textStages) {
                when (stage) {
                    PipelineStage.Recombination -> {
                        text = recombine(requireNotNull(postProcessor), text)
                        recombined = text
                    }
                    else -> Unit
                }
            }
            emit(
                SignToTextResult(
                    label = prediction.label,
                    text = text,
                    recombinedText = recombined,
                    confidence = prediction.confidence,
                    span = SegmentSpan(prediction.windowStartMillis, prediction.windowEndMillis)
                )
            )
        }
    }

    private suspend fun recombine(processor: PostProcessor, text: String): String {
        val builder = StringBuilder()
        processor.process(text).collect { piece -> builder.append(piece) }
        return builder.toString().ifEmpty { text }
    }
}
