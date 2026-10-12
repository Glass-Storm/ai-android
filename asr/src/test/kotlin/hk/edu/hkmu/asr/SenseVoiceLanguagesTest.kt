package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tier A acceptance for [SenseVoiceLanguages] mapping. */
class SenseVoiceLanguagesTest {

    @Test
    fun `supported tags map to SenseVoice model codes`() {
        assertEquals("auto", SenseVoiceLanguages.toModelCode(Language.Auto))
        assertEquals("en", SenseVoiceLanguages.toModelCode(Language.English))
        assertEquals("yue", SenseVoiceLanguages.toModelCode(Language.Cantonese))
        assertEquals("zh", SenseVoiceLanguages.toModelCode(Language.Mandarin))
        assertEquals("ja", SenseVoiceLanguages.toModelCode(Language.Japanese))
        assertEquals("ko", SenseVoiceLanguages.toModelCode(Language.Korean))
    }

    @Test
    fun `unsupported tag raises UnsupportedLanguage`() {
        assertThrows(AiAndroidException.UnsupportedLanguage::class.java) {
            SenseVoiceLanguages.toModelCode(Language("fr"))
        }
    }

    @Test
    fun `detected tags map back to core languages`() {
        assertEquals(Language.English, SenseVoiceLanguages.toLanguage("en"))
        assertEquals(Language.Cantonese, SenseVoiceLanguages.toLanguage("yue"))
        assertEquals(Language.Mandarin, SenseVoiceLanguages.toLanguage("zh"))
        assertNull(SenseVoiceLanguages.toLanguage("xx"))
    }
}
