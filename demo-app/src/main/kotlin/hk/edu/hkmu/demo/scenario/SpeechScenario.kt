@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.pipeline.PipelineSpec
import hk.edu.hkmu.core.pipeline.SpeechToTextPipeline
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

/**
 * Scenario 3: the real speech pipeline.
 *
 * [SpeechToTextPipeline] composes the real `Asr -> Recombination -> Translation` order declared by
 * [PipelineSpec.SpeechToText]. Only the engines are test-kit fakes: a scripted [FakeAsrEngine] emits a
 * final segment, the [FakePostProcessor] recombines it, and the [FakeTranslationEngine] translates it.
 */
object SpeechScenario {
    /** Runs one scripted utterance through the pipeline and renders each stage output. */
    suspend fun run(): String {
        val asr = FakeAsrEngine(listOf(TranscriptionSegment("hello world", 0L, 500L)))
        val postProcessor = FakePostProcessor(listOf("Hello", " ", "World"))
        val translation = FakeTranslationEngine(
            listOf(TranslatedText("你好世界", Language.English, Language.Mandarin))
        )
        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.SpeechToText,
            asr = asr,
            translation = translation,
            postProcessor = postProcessor
        )
        val audio = flowOf(AudioChunk(floatArrayOf(0f, 0.25f, -0.25f)))
        val results = pipeline.transcribe(audio).toList()

        return buildString {
            appendLine("SpeechToTextPipeline stages: ${pipeline.stages.joinToString(" -> ")}")
            appendLine("Input: 1 AudioChunk (mono float PCM, 16 kHz) -> 1 final ASR segment")
            results.forEach { result ->
                appendLine("  sourceText     = \"${result.sourceText}\"")
                appendLine("  recombinedText = \"${result.recombinedText}\"")
                appendLine("  translatedText = \"${result.translatedText}\"")
                appendLine("  span           = ${result.span.startMillis}..${result.span.endMillis} ms")
            }
            append("Engines: FakeAsrEngine + FakePostProcessor + FakeTranslationEngine (test kit)")
        }
    }
}
