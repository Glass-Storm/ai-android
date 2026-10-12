package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Maps the library's open-ended BCP-47 [Language] tags to NLLB-200 FLORES-200 codes.
 *
 * NLLB selects source/target language through dedicated added tokens (absent from the
 * SentencePiece vocabulary), so a code must be resolved explicitly before tokenization. A FLORES
 * code passed straight through is accepted verbatim, so a host can always address a language
 * unambiguously. `zh` (Mandarin) resolves to Simplified (`zho_Hans`); Traditional is addressed as
 * `zh-Hant`. Any tag that cannot be mapped raises [AiAndroidException.UnsupportedLanguage] so a
 * caller fails fast instead of silently feeding the model an `<unk>` language token.
 */
object NllbLanguageTags {
    /** FLORES code for English. */
    const val ENGLISH: String = "eng_Latn"

    /** FLORES code for Simplified Mandarin. */
    const val MANDARIN_SIMPLIFIED: String = "zho_Hans"

    /** FLORES code for Traditional Mandarin. */
    const val MANDARIN_TRADITIONAL: String = "zho_Hant"

    /** FLORES code for Cantonese (written Traditional). */
    const val CANTONESE: String = "yue_Hant"

    private val codesByTag: Map<String, String> = mapOf(
        "en" to ENGLISH,
        "en-us" to ENGLISH,
        "en-gb" to ENGLISH,
        "eng" to ENGLISH,
        "eng_latn" to ENGLISH,
        "english" to ENGLISH,
        "zh" to MANDARIN_SIMPLIFIED,
        "zh-hans" to MANDARIN_SIMPLIFIED,
        "zh-cn" to MANDARIN_SIMPLIFIED,
        "zh-sg" to MANDARIN_SIMPLIFIED,
        "cmn" to MANDARIN_SIMPLIFIED,
        "zho_hans" to MANDARIN_SIMPLIFIED,
        "mandarin" to MANDARIN_SIMPLIFIED,
        "zh-hant" to MANDARIN_TRADITIONAL,
        "zh-tw" to MANDARIN_TRADITIONAL,
        "zh-hk" to MANDARIN_TRADITIONAL,
        "zho_hant" to MANDARIN_TRADITIONAL,
        "yue" to CANTONESE,
        "yue-hant" to CANTONESE,
        "yue-hk" to CANTONESE,
        "yue_hant" to CANTONESE,
        "cantonese" to CANTONESE
    )

    /**
     * @return the FLORES-200 code for [language].
     * @throws AiAndroidException.UnsupportedLanguage when [language] is not a mapped NLLB language.
     */
    fun toFloresCode(language: Language): String =
        codesByTag[language.tag.lowercase()] ?: throw AiAndroidException.UnsupportedLanguage(language)
}
