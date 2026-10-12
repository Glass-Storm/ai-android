@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the deterministic semantics of every engine fake. */
class FakeEnginesTest {

    @Test
    fun `asr emits segments in order and completes`() = runTest {
        val segments = listOf(segment("a", 0), segment("b", 10))
        val engine = FakeAsrEngine(segments)

        val emitted = engine.transcribe(emptyFlow()).toList()

        assertEquals(segments, emitted)
        assertTrue(engine.completed)
        assertFalse(engine.cancelled)
        assertFalse(engine.failed)
    }

    @Test
    fun `asr surfaces injected failure`() = runTest {
        val engine = FakeAsrEngine(failure = AiAndroidException.InputTooLong(5, 3))

        val error = runCatching { engine.transcribe(emptyFlow()).toList() }.exceptionOrNull()

        assertTrue(error is AiAndroidException.InputTooLong)
        assertTrue(engine.failed)
        assertFalse(engine.completed)
    }

    @Test
    fun `tts emits a default non-empty chunk and records the input`() = runTest {
        val engine = FakeTtsEngine()

        val emitted = engine.synthesize("hello").toList()

        assertEquals(1, emitted.size)
        assertTrue(emitted.first().samples.isNotEmpty())
        assertEquals("hello", engine.lastText)
        assertTrue(engine.completed)
    }

    @Test
    fun `translation emits translated text`() = runTest {
        val translation = TranslatedText("你好", Language.English, Language.Mandarin)
        val engine = FakeTranslationEngine(listOf(translation))

        val emitted = engine.translate("hello").toList()

        assertEquals(listOf(translation), emitted)
        assertEquals("hello", engine.lastText)
    }

    @Test
    fun `sign emits one prediction per window`() = runTest {
        val prediction = SignPrediction("HELLO", 0.9f, 0, 900)
        val engine = FakeSignEngine(listOf(prediction))
        val frames = flowOf(SignFrame(FloatArray(1), 0), SignFrame(FloatArray(1), 100))

        val emitted = engine.recognize(frames).toList()

        assertEquals(listOf(prediction), emitted)
        assertEquals(2, engine.consumedFrames)
    }

    @Test
    fun `post processor splits the input into a token stream`() = runTest {
        val engine = FakePostProcessor()

        val emitted = engine.process("hello world").toList()

        assertEquals(listOf("hello", "world"), emitted)
        assertEquals("hello world", engine.lastInput)
    }

    @Test
    fun `engine factory returns injected fakes and records configs`() {
        val asr = FakeAsrEngine()
        val factory = FakeEngineFactory(asr = asr, tts = FakeTtsEngine())

        assertSame(asr, factory.createAsrEngine(AsrConfig()))
        factory.createTtsEngine(TtsConfig())
        factory.createTranslationEngine(TranslationConfig())
        factory.createSignEngine(SignConfig(classifierId = "asl"))
        factory.createPostProcessor(PostProcessConfig())

        assertEquals(1, factory.asrConfigs.size)
        assertEquals(1, factory.ttsConfigs.size)
        assertEquals(1, factory.translationConfigs.size)
        assertEquals("asl", factory.signConfigs.single().classifierId)
        assertEquals(1, factory.postProcessConfigs.size)
    }

    private fun segment(text: String, startMillis: Long): TranscriptionSegment =
        TranscriptionSegment(text, startMillis, startMillis + 10, language = Language.English)
}
