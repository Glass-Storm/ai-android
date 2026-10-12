@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the human-readable composition observables the task requires — the two
 * default orders, the `ASR -> recombine -> translate` order as each stage saw its input, the
 * configurable `translate -> recombine` swap, the recombination opt-out, and the adversarial
 * `PipelineMisconfigured`.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :core:testDebugUnitTest --tests '*PipelineManualQaProbeTest' -i --rerun-tasks`.
 */
class PipelineManualQaProbeTest {

    @Test
    fun `manual QA - declared orders and ASR-recombine-translate order`() = runTest {
        println("PIPELINE-MANUAL-QA speech stages=${PipelineSpec.SpeechToText.stages}")
        println("PIPELINE-MANUAL-QA sign stages=${PipelineSpec.SignToText.stages}")

        val asr = FakeAsrEngine(listOf(TranscriptionSegment("hello world", 0, 100)))
        val postProcessor = FakePostProcessor(listOf("HELLO", " ", "WORLD"))
        val translation = FakeTranslationEngine(listOf(TranslatedText("你好", Language.English, Language.Mandarin)))
        val pipeline = SpeechToTextPipeline(PipelineSpec.SpeechToText, asr, translation, postProcessor)

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        println(
            "PIPELINE-MANUAL-QA order ASR->recombine->translate " +
                "postProcessorInput='${postProcessor.lastInput}' translationInput='${translation.lastText}' " +
                "final='${result.text}'"
        )
        assertTrue(postProcessor.lastInput == "hello world")
        assertTrue(translation.lastText == "HELLO WORLD")
        assertTrue(result.text == "你好")
    }

    @Test
    fun `manual QA - configurable translation-before-recombination order`() = runTest {
        val asr = FakeAsrEngine(listOf(TranscriptionSegment("hello world", 0, 100)))
        val postProcessor = FakePostProcessor(listOf("你好", "!"))
        val translation = FakeTranslationEngine(listOf(TranslatedText("你好", Language.English, Language.Mandarin)))
        val spec = PipelineSpec.of(
            PipelineKind.SpeechToText,
            listOf(PipelineStage.Asr, PipelineStage.Translation, PipelineStage.Recombination)
        )

        val result = SpeechToTextPipeline(spec, asr, translation, postProcessor)
            .transcribe(emptyFlow()).toList().single()

        println(
            "PIPELINE-MANUAL-QA order ASR->translate->recombine " +
                "translationInput='${translation.lastText}' postProcessorInput='${postProcessor.lastInput}' " +
                "final='${result.text}'"
        )
        assertTrue(translation.lastText == "hello world")
        assertTrue(postProcessor.lastInput == "你好")
        assertTrue(result.text == "你好!")
    }

    @Test
    fun `manual QA - adversarial recombination-before-ASR is misconfigured`() {
        val failure = runCatching {
            PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Recombination, PipelineStage.Asr))
        }.exceptionOrNull()
        val reason = (failure as? AiAndroidException.PipelineMisconfigured)?.reason

        println(
            "PIPELINE-MANUAL-QA adversarial recombinationBeforeAsr " +
                "type=${failure?.javaClass?.simpleName} reason='$reason'"
        )
        assertNotNull(failure)
        assertTrue(failure is AiAndroidException.PipelineMisconfigured)
        assertNotNull(reason)
    }

    @Test
    fun `manual QA - recombination opt-out skips the lazy post-processor`() = runTest {
        val asr = FakeAsrEngine(listOf(TranscriptionSegment("hello world", 0, 100)))
        val postProcessor = FakePostProcessor(listOf("HELLO"))
        val translation = FakeTranslationEngine(listOf(TranslatedText("你好", Language.English, Language.Mandarin)))
        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.SpeechToText,
            asr = asr,
            translation = translation,
            postProcessor = postProcessor,
            recombinationEnabled = false
        )

        pipeline.transcribe(emptyFlow()).toList()

        println(
            "PIPELINE-MANUAL-QA optOut effectiveStages=${pipeline.effectiveStages} " +
                "postProcessorInvoked=${postProcessor.lastInput != null}"
        )
        assertNull(postProcessor.lastInput)
    }
}
