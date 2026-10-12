@file:Suppress("MagicNumber")

package hk.edu.hkmu.fixture

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.di.AiAndroidGraph
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.pipeline.PipelineKind
import hk.edu.hkmu.core.pipeline.PipelineSpec
import hk.edu.hkmu.core.pipeline.PipelineStage
import hk.edu.hkmu.core.pipeline.SignToTextPipeline
import hk.edu.hkmu.core.pipeline.SpeechToTextPipeline
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import hk.edu.hkmu.testkit.engine.FakeTtsEngine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A error-mapping contract: every engine failure must surface as its **precise typed**
 * [AiAndroidException] subclass through the public facade and composed pipelines, an upstream failure
 * must short-circuit downstream stages, and an incomplete pipeline wiring must fail fast with
 * [AiAndroidException.PipelineMisconfigured] (never a null pointer at collection time).
 *
 * The failures are injected through the `testkit` fakes; the important property is the *type and
 * identity* of the error crossing the facade boundary, not the message text.
 */
class ErrorMappingTest {

    @Test
    fun `tts failure surfaces its typed error through the facade`() = runBlocking {
        val failure = AiAndroidException.UnsupportedLanguage(Language.Japanese)
        val engine = FakeTtsEngine(failure = failure)
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(tts = engine)).tts(TtsConfig()).build()

        val error = runCatching { graph.tts().synthesize("テスト").toList() }.exceptionOrNull()

        assertSame(failure, error)
        assertEquals(Language.Japanese, (error as AiAndroidException.UnsupportedLanguage).language)
        assertTrue("the TTS fake recorded the failure", engine.failed)
    }

    @Test
    fun `translation failure surfaces its typed error through the facade`() = runBlocking {
        val failure = AiAndroidException.InputTooLong(actualLength = 900, maxLength = 512)
        val engine = FakeTranslationEngine(failure = failure)
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(translation = engine))
            .translation(TranslationConfig()).build()

        val error = runCatching { graph.translation().translate("long").toList() }.exceptionOrNull()

        assertSame(failure, error)
        assertEquals(512, (error as AiAndroidException.InputTooLong).maxLength)
    }

    @Test
    fun `sign failure surfaces its typed error through the composed sign pipeline`() = runBlocking {
        val failure = AiAndroidException.MalformedFrame("missing hand landmarks")
        val sign = FakeSignEngine(failure = failure)
        val pipeline = SignToTextPipeline(
            spec = PipelineSpec.of(PipelineKind.SignToText, listOf(PipelineStage.Sign)),
            sign = sign
        )

        val error = runCatching { pipeline.recognize(emptyFlow()).toList() }.exceptionOrNull()

        assertSame(failure, error)
        assertTrue((error as AiAndroidException.MalformedFrame).reason.contains("missing hand"))
    }

    @Test
    fun `an upstream asr failure short-circuits every downstream stage`() = runBlocking {
        val failure = AiAndroidException.ModelLoadFailed("sense-voice", null)
        val asr = FakeAsrEngine(failure = failure)
        val translation = FakeTranslationEngine(listOf(TranslatedText("你好", Language.English, Language.Mandarin)))
        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Asr, PipelineStage.Translation)),
            asr = asr,
            translation = translation
        )

        val error = runCatching { pipeline.transcribe(emptyFlow()).toList() }.exceptionOrNull()

        assertSame(failure, error)
        assertNull("translation must not run after an asr failure", translation.lastText)
        assertTrue("the asr fake recorded the failure", asr.failed)
    }

    @Test
    fun `a declared stage with no engine fails fast with PipelineMisconfigured`() {
        val error = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SpeechToTextPipeline(
                spec = PipelineSpec.of(
                    PipelineKind.SpeechToText,
                    listOf(PipelineStage.Asr, PipelineStage.Translation)
                ),
                asr = FakeAsrEngine()
            )
        }

        assertTrue("reason names the missing stage", error.reason.contains(PipelineStage.Translation.name))
    }

    @Test
    fun `the facade reports a missing stage with a typed MissingDependency`() {
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory()).asr(AsrConfig()).build()

        val error = assertThrows(AiAndroidException.MissingDependency::class.java) { graph.sign() }

        assertEquals("SignEngine", error.dependency)
    }
}
