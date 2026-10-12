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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A: the speech composition runs its configured order over the core engine interfaces. The fakes
 * record the input they received, so the order is proven by *what each stage saw* — ASR → recombination
 * → translation is asserted, and the translation → recombination swap proves the order is configurable.
 */
class SpeechToTextPipelineTest {

    @Test
    fun `ASR then recombination then translation runs in the configured order`() = runTest {
        val asr = FakeAsrEngine(listOf(segment("hello world")))
        val postProcessor = FakePostProcessor(listOf("HELLO", " ", "WORLD"))
        val translation = FakeTranslationEngine(listOf(translated("你好")))

        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.SpeechToText,
            asr = asr,
            translation = translation,
            postProcessor = postProcessor
        )

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        // Recombination saw the raw ASR text (ASR ran first)...
        assertEquals("hello world", postProcessor.lastInput)
        // ...and translation saw the recombined text (recombination ran before translation).
        assertEquals("HELLO WORLD", translation.lastText)
        assertEquals("hello world", result.sourceText)
        assertEquals("HELLO WORLD", result.recombinedText)
        assertEquals("你好", result.translatedText)
        assertEquals("你好", result.text)
        assertEquals(1, asr.emittedSegments.size)
    }

    @Test
    fun `translation before recombination is a configurable order`() = runTest {
        val asr = FakeAsrEngine(listOf(segment("hello world")))
        val postProcessor = FakePostProcessor(listOf("你好", "!"))
        val translation = FakeTranslationEngine(listOf(translated("你好")))

        val spec = PipelineSpec.of(
            PipelineKind.SpeechToText,
            listOf(PipelineStage.Asr, PipelineStage.Translation, PipelineStage.Recombination)
        )
        val pipeline = SpeechToTextPipeline(spec, asr, translation, postProcessor)

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        // Translation saw the raw text (it ran first); recombination saw the translated text.
        assertEquals("hello world", translation.lastText)
        assertEquals("你好", postProcessor.lastInput)
        assertEquals("你好", result.translatedText)
        assertEquals("你好!", result.recombinedText)
        assertEquals("你好!", result.text)
    }

    @Test
    fun `recombination opt-out skips the lazy post-processor and translates the raw text`() = runTest {
        val asr = FakeAsrEngine(listOf(segment("hello world")))
        val postProcessor = FakePostProcessor(listOf("HELLO", " ", "WORLD"))
        val translation = FakeTranslationEngine(listOf(translated("你好")))

        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.SpeechToText,
            asr = asr,
            translation = translation,
            postProcessor = postProcessor,
            recombinationEnabled = false
        )

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        assertNull("post-processor never invoked", postProcessor.lastInput)
        assertTrue("no tokens emitted", postProcessor.emittedTokens.isEmpty())
        assertEquals("hello world", translation.lastText)
        assertNull(result.recombinedText)
        assertEquals("你好", result.translatedText)
        assertEquals(listOf(PipelineStage.Asr, PipelineStage.Translation), pipeline.effectiveStages)
    }

    @Test
    fun `omitting translation yields the recombined text`() = runTest {
        val spec = PipelineSpec.of(
            PipelineKind.SpeechToText,
            listOf(PipelineStage.Asr, PipelineStage.Recombination)
        )
        val pipeline = SpeechToTextPipeline(
            spec = spec,
            asr = FakeAsrEngine(listOf(segment("hi"))),
            postProcessor = FakePostProcessor(listOf("HI"))
        )

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        assertEquals("HI", result.text)
        assertEquals("HI", result.recombinedText)
        assertNull(result.translatedText)
    }

    @Test
    fun `source-only spec emits the raw ASR text`() = runTest {
        val spec = PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Asr))
        val pipeline = SpeechToTextPipeline(spec, FakeAsrEngine(listOf(segment("raw"))))

        val result = pipeline.transcribe(emptyFlow()).toList().single()

        assertEquals("raw", result.text)
        assertNull(result.recombinedText)
        assertNull(result.translatedText)
    }

    @Test
    fun `declaring translation without an engine fails fast`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SpeechToTextPipeline(
                spec = PipelineSpec.SpeechToText,
                asr = FakeAsrEngine(),
                postProcessor = FakePostProcessor()
            )
        }

        assertTrue(failure.reason.contains("TranslationEngine"))
    }

    @Test
    fun `declaring recombination without a post-processor fails fast`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SpeechToTextPipeline(
                spec = PipelineSpec.SpeechToText,
                asr = FakeAsrEngine(),
                translation = FakeTranslationEngine()
            )
        }

        assertTrue(failure.reason.contains("PostProcessor"))
    }

    @Test
    fun `a sign spec cannot drive the speech pipeline`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SpeechToTextPipeline(spec = PipelineSpec.SignToText, asr = FakeAsrEngine())
        }

        assertTrue(failure.reason.contains("SpeechToText"))
    }

    private fun segment(text: String): TranscriptionSegment =
        TranscriptionSegment(text = text, startTimeMillis = 0, endTimeMillis = 100)

    private fun translated(text: String): TranslatedText =
        TranslatedText(text = text, sourceLanguage = Language.English, targetLanguage = Language.Mandarin)
}
