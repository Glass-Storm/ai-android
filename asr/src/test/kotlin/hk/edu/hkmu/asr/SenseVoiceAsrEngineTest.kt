@file:Suppress("MagicNumber")

package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance for [SenseVoiceAsrEngine]: ordered final segments through the fake seam, the
 * empty-audio adversarial probe, language handling and the 16 kHz input contract.
 */
class SenseVoiceAsrEngineTest {

    @Test
    fun `emits ordered final segments and completes`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(
            texts = listOf("utt-0", "utt-1", "utt-2"),
            detectedTags = listOf("en", "yue", "zh")
        )
        val segments = engine(recognizer).transcribe(threeUtterances()).toList()

        assertEquals(listOf("utt-0", "utt-1", "utt-2"), segments.map { it.text })
        assertTrue("every segment is final", segments.all { it.isFinal })
        assertEquals(3, recognizer.recognizedSampleCounts.size)
        assertTrue(
            "segments are chronological",
            segments.zipWithNext().all { (first, second) -> first.startTimeMillis <= second.startTimeMillis }
        )
        assertEquals(
            listOf(Language.English, Language.Cantonese, Language.Mandarin),
            segments.map { it.language }
        )
    }

    @Test
    fun `empty audio yields empty result and completes without calling the recognizer`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(texts = listOf("never"))

        val segments = engine(recognizer).transcribe(emptyFlow()).toList()

        assertTrue("no segments for empty audio", segments.isEmpty())
        assertTrue("recognizer never invoked", recognizer.recognizedSampleCounts.isEmpty())
    }

    @Test
    fun `blank recognition results are not emitted`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(texts = listOf("   "))

        val segments = engine(recognizer).transcribe(oneUtterance()).toList()

        assertTrue(segments.isEmpty())
        assertEquals(1, recognizer.recognizedSampleCounts.size)
    }

    @Test
    fun `explicit language is attached to segments`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(texts = listOf("hello"), detectedTags = listOf("zh"))

        val segments = engine(recognizer, AsrConfig(language = Language.English))
            .transcribe(oneUtterance())
            .toList()

        assertEquals(listOf(Language.English), segments.map { it.language })
    }

    @Test
    fun `close releases the lazily created recognizer`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(texts = listOf("hello"))
        val engine = engine(recognizer)
        engine.transcribe(oneUtterance()).toList()
        assertFalse("recognizer created on first collection", recognizer.closed)

        engine.close()

        assertTrue("close() releases the recognizer", recognizer.closed)
    }

    @Test
    fun `non 16 kHz audio is rejected with MalformedFrame`() {
        val engine = engine(FakeSenseVoiceRecognizer())
        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            runBlocking {
                engine.transcribe(flowOf(AudioChunk(FloatArray(SHORT_CHUNK), MISMATCHED_RATE_HZ))).toList()
            }
        }
        assertTrue(exception.message.orEmpty().contains("16000"))
    }

    @Test
    fun `unsupported language fails fast at construction`() {
        assertThrows(AiAndroidException.UnsupportedLanguage::class.java) {
            SenseVoiceAsrEngine.forTesting(
                AsrConfig(language = Language("fr")),
                FakeSenseVoiceRecognizer(),
                Dispatchers.Unconfined
            )
        }
    }

    private fun engine(recognizer: SenseVoiceRecognizer, config: AsrConfig = AsrConfig()) =
        SenseVoiceAsrEngine.forTesting(config, recognizer, Dispatchers.Unconfined)

    private fun threeUtterances() = SyntheticAudio.chunks(
        SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS) +
            SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS) +
            SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS),
        chunkSize = CHUNK_SAMPLES
    ).asFlow()

    private fun oneUtterance() = SyntheticAudio.chunks(
        SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS),
        chunkSize = CHUNK_SAMPLES
    ).asFlow()

    private companion object {
        const val SPEECH_MS = 600
        const val PAUSE_MS = 600
        const val CHUNK_SAMPLES = 1_000
        const val SHORT_CHUNK = 100
        const val MISMATCHED_RATE_HZ = 8_000
    }
}
