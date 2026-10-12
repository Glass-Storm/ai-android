package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Maps [Language] tags to SenseVoice model language codes and back.
 *
 * SenseVoice int8 (`zh-en-ja-ko-yue`) accepts `auto`, `en`, `yue`, `zh`, `ja`, `ko`. Anything else
 * is surfaced as a typed [AiAndroidException.UnsupportedLanguage] rather than being silently fed to
 * native (which would fall back to `auto` and surprise the caller).
 */
internal object SenseVoiceLanguages {

    private val modelCodeByTag: Map<String, String> = mapOf(
        Language.Auto.tag to "auto",
        Language.English.tag to "en",
        Language.Cantonese.tag to "yue",
        Language.Mandarin.tag to "zh",
        Language.Japanese.tag to "ja",
        Language.Korean.tag to "ko"
    )

    /** Resolves the SenseVoice language code for [language]. */
    fun toModelCode(language: Language): String = modelCodeByTag[language.tag.lowercase()]
        ?: throw AiAndroidException.UnsupportedLanguage(language)

    /** Maps a model-reported language tag back to a core [Language], or null when unknown. */
    fun toLanguage(detectedTag: String): Language? = when (detectedTag.lowercase()) {
        Language.English.tag -> Language.English
        Language.Cantonese.tag -> Language.Cantonese
        Language.Mandarin.tag -> Language.Mandarin
        Language.Japanese.tag -> Language.Japanese
        Language.Korean.tag -> Language.Korean
        else -> null
    }
}
