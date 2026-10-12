@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance for [NllbTranslationEngine]: fake-path round-trips, the mandatory decoder seed,
 * the source layout, the 512-token limit, the emission contract and seam lifecycle.
 */
class NllbTranslationEngineTest {

    @Test
    fun `round-trips text through the seams and seeds the decoder with bos plus target lang`() = runBlocking {
        val text = "Hello"
        val tokenizer = FakeNllbTokenizer()
        val session = FakeNllbSession(text.map { it.code + 1 })
        val config = TranslationConfig(sourceLanguage = Language.English, targetLanguage = Language.Cantonese)
        val engine = NllbTranslationEngine.forTesting(config, tokenizer, session)

        val results = engine.translate(text).toList()

        assertEquals(1, results.size)
        val translated = results.single()
        assertEquals(text, translated.text)
        assertEquals(Language.English, translated.sourceLanguage)
        assertEquals(Language.Cantonese, translated.targetLanguage)
        assertEquals(listOf(2L, 256_199L), session.decoderSeeds.single())
        assertEquals(listOf(256_047L) + text.map { it.code + 1L } + 2L, session.encodedInputs.single().toList())
    }

    @Test
    fun `mandarin target maps to the simplified flores id`() = runBlocking {
        val tokenizer = FakeNllbTokenizer()
        val session = FakeNllbSession(listOf(2))
        val config = TranslationConfig(sourceLanguage = Language.English, targetLanguage = Language.Mandarin)
        val engine = NllbTranslationEngine.forTesting(config, tokenizer, session)

        engine.translate("Hi").toList()

        assertEquals(256_200L, session.decoderSeeds.single()[1])
    }

    @Test
    fun `source over the context window raises InputTooLong`() {
        val engine = NllbTranslationEngine.forTesting(
            TranslationConfig(sourceLanguage = Language.English, targetLanguage = Language.Cantonese),
            FakeNllbTokenizer(),
            FakeNllbSession()
        )

        val exception = assertThrows(AiAndroidException.InputTooLong::class.java) {
            runBlocking { engine.translate("a".repeat(512)).toList() }
        }

        assertEquals(514, exception.actualLength)
        assertEquals(512, exception.maxLength)
    }

    @Test
    fun `immediate eos yields empty text`() = runBlocking {
        val engine = NllbTranslationEngine.forTesting(
            TranslationConfig(),
            FakeNllbTokenizer(),
            FakeNllbSession()
        )

        val result = engine.translate("x").toList().single()

        assertEquals("", result.text)
    }

    @Test
    fun `unsupported language fails fast at construction`() {
        assertThrows(AiAndroidException.UnsupportedLanguage::class.java) {
            NllbTranslationEngine.forTesting(
                TranslationConfig(sourceLanguage = Language("fr")),
                FakeNllbTokenizer(),
                FakeNllbSession()
            )
        }
    }

    @Test
    fun `close releases the lazily created seams`() = runBlocking {
        val tokenizer = FakeNllbTokenizer()
        val session = FakeNllbSession(listOf(2))
        val engine = NllbTranslationEngine.forTesting(TranslationConfig(), tokenizer, session)
        engine.translate("Hi").toList()
        assertFalse("seams created on first collection", session.closed)

        engine.close()

        assertTrue(session.closed)
        assertTrue(tokenizer.closed)
    }
}
