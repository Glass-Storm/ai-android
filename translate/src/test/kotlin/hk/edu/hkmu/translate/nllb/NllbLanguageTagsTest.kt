@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tier A acceptance for the core-tag -> NLLB FLORES vocabulary mapping. */
class NllbLanguageTagsTest {

    @Test
    fun `maps english`() {
        assertEquals("eng_Latn", NllbLanguageTags.toFloresCode(Language.English))
    }

    @Test
    fun `maps mandarin default to simplified`() {
        assertEquals("zho_Hans", NllbLanguageTags.toFloresCode(Language.Mandarin))
    }

    @Test
    fun `maps zh-Hans tag`() {
        assertEquals("zho_Hans", NllbLanguageTags.toFloresCode(Language("zh-Hans")))
    }

    @Test
    fun `maps zh-Hant tag`() {
        assertEquals("zho_Hant", NllbLanguageTags.toFloresCode(Language("zh-Hant")))
    }

    @Test
    fun `maps cantonese to yue_Hant`() {
        assertEquals("yue_Hant", NllbLanguageTags.toFloresCode(Language.Cantonese))
    }

    @Test
    fun `accepts an explicit flores code verbatim`() {
        assertEquals("yue_Hant", NllbLanguageTags.toFloresCode(Language("yue_Hant")))
    }

    @Test
    fun `rejects an unsupported language`() {
        assertThrows(AiAndroidException.UnsupportedLanguage::class.java) {
            NllbLanguageTags.toFloresCode(Language("fr"))
        }
    }
}
