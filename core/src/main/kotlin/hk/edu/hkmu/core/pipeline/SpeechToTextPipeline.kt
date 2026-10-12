package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * Composed speech pipeline over the core engine interfaces.
 *
 * ### Composition (owned here, never by an engine)
 * ```
 * Flow<AudioChunk> -> Asr -> (Recombination) -> (Translation) -> Flow<SpeechResult>
 * ```
 * The order is exactly the order declared by [spec]; this class applies the stages in that order and
 * does not reorder them. Recombination sits between ASR and translation in the default spec and is
 * **default-ON but lazy**: it is only invoked per emitted segment and only when [recombinationEnabled]
 * is true, so a host that opts out never touches the (lazily loaded) LLM.
 *
 * ### Misconfiguration is typed and fail-fast
 * A spec whose order is illegal is rejected when the [PipelineSpec] is built; a spec of the wrong
 * [PipelineKind], or a declared stage with no engine, is rejected by this constructor with
 * [AiAndroidException.PipelineMisconfigured] before any audio is consumed.
 *
 * @property spec Explicit stage order (source first).
 * @param asr Speech-to-text engine (required).
 * @param translation Translation engine; required only when the effective stages include translation.
 * @param postProcessor Recombination post-processor; required only when recombination is enabled and
 *   declared.
 * @param recombinationEnabled Recombination opt-out; when false the stage is skipped lazily.
 */
class SpeechToTextPipeline(
    val spec: PipelineSpec,
    private val asr: AsrEngine,
    private val translation: TranslationEngine? = null,
    private val postProcessor: PostProcessor? = null,
    private val recombinationEnabled: Boolean = true
) {

    private val plan: ResolvedPipeline = spec.resolve(recombinationEnabled)

    /** Declared stage order exactly as configured (source first). */
    val stages: List<PipelineStage> = spec.stages

    /** Stages that actually execute, in order, after the recombination opt-out is applied. */
    val effectiveStages: List<PipelineStage> = listOf(plan.source) + plan.textStages

    init {
        val failure = if (plan.kind != PipelineKind.SpeechToText) {
            "SpeechToTextPipeline requires a ${PipelineKind.SpeechToText.name} spec, got ${plan.kind.name}"
        } else {
            missingEngineFailure(plan.textStages, translation != null, postProcessor != null)
        }
        if (failure != null) {
            throw AiAndroidException.PipelineMisconfigured(failure)
        }
    }

    /** Transcribes [audio] and runs every configured text stage in order. */
    fun transcribe(audio: Flow<AudioChunk>): Flow<SpeechResult> = flow {
        asr.transcribe(audio).collect { segment ->
            var current = segment.text
            var recombined: String? = null
            var translated: String? = null
            for (stage in plan.textStages) {
                when (stage) {
                    PipelineStage.Recombination -> {
                        current = recombine(requireNotNull(postProcessor), current)
                        recombined = current
                    }
                    PipelineStage.Translation -> {
                        current = translate(requireNotNull(translation), current)
                        translated = current
                    }
                    else -> Unit
                }
            }
            emit(
                SpeechResult(
                    sourceText = segment.text,
                    text = current,
                    recombinedText = recombined,
                    translatedText = translated,
                    span = SegmentSpan(segment.startTimeMillis, segment.endTimeMillis)
                )
            )
        }
    }

    private suspend fun recombine(processor: PostProcessor, text: String): String {
        val builder = StringBuilder()
        processor.process(text).collect { piece -> builder.append(piece) }
        return builder.toString().ifEmpty { text }
    }

    private suspend fun translate(engine: TranslationEngine, text: String): String {
        val translated = engine.translate(text).toList()
        return translated.joinToString(separator = "") { it.text }.ifEmpty { text }
    }
}
