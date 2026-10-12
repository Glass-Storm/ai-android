package hk.edu.hkmu.core.config

/**
 * A BCP-47 / engine language tag.
 *
 * Language is deliberately a value class instead of an enum: ASR accepts `auto` plus concrete
 * spoken languages, while the translator uses NLLB tags such as `yue_Hant`. Each engine maps these
 * tags to its own identifiers and raises an `UnsupportedLanguage` error for anything it cannot
 * serve, so the set of tags is open-ended.
 */
@JvmInline
value class Language(val tag: String) {
    companion object {
        /** Auto-detect (ASR only). */
        val Auto = Language("auto")

        /** English. */
        val English = Language("en")

        /** Cantonese (spoken). */
        val Cantonese = Language("yue")

        /** Mandarin / Chinese. */
        val Mandarin = Language("zh")

        /** Japanese. */
        val Japanese = Language("ja")

        /** Korean. */
        val Korean = Language("ko")
    }
}
