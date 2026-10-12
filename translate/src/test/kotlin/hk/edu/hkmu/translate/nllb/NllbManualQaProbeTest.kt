@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe. Prints the human-readable observables the task requires; run with `-i` to
 * capture the `STANDARD_OUT` block.
 */
class NllbManualQaProbeTest {

    @Test
    fun `manual qa happy path maps codes and round-trips through the seams`() = runBlocking {
        println("NLLB-MANUAL-QA mapping:")
        listOf(Language.English, Language("zh-Hans"), Language("zh-Hant"), Language.Cantonese).forEach {
            println("NLLB-MANUAL-QA   ${it.tag} -> ${NllbLanguageTags.toFloresCode(it)}")
        }

        val text = "Hello today"
        val tokenizer = FakeNllbTokenizer()
        val session = FakeNllbSession(text.map { it.code + 1 })
        val config = TranslationConfig(sourceLanguage = Language.English, targetLanguage = Language.Cantonese)
        val engine = NllbTranslationEngine.forTesting(config, tokenizer, session)

        val result = engine.translate(text).toList().single()
        println("NLLB-MANUAL-QA round-trip input=\"$text\" output=\"${result.text}\"")
        println("NLLB-MANUAL-QA decoder-seed=${session.decoderSeeds.single()}")
        println("NLLB-MANUAL-QA source-ids=${session.encodedInputs.single().toList()}")

        assertEquals(text, result.text)
        assertEquals(listOf(2L, 256_199L), session.decoderSeeds.single())
    }

    @Test
    fun `manual qa adversarial input too long and commercial license`() {
        val engine = NllbTranslationEngine.forTesting(
            TranslationConfig(sourceLanguage = Language.English, targetLanguage = Language.Cantonese),
            FakeNllbTokenizer(),
            FakeNllbSession()
        )
        val tooLong = assertThrows(AiAndroidException.InputTooLong::class.java) {
            runBlocking { engine.translate("a".repeat(700)).toList() }
        }
        println("NLLB-MANUAL-QA adversarial InputTooLong actual=${tooLong.actualLength} max=${tooLong.maxLength}")

        val licensed = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            NllbTranslationEngine.create(
                TranslationConfig(),
                File("/tmp/nllb-model-absent"),
                DefaultLicensePolicy(),
                UsageIntent.Commercial
            )
        }
        println(
            "NLLB-MANUAL-QA adversarial LicenseRestricted model=${licensed.modelId} " +
                "license=${licensed.licenseKind} intent=${licensed.intent}"
        )

        assertEquals(702, tooLong.actualLength)
        assertTrue(licensed.message.orEmpty().contains("License restricted"))
    }
}
