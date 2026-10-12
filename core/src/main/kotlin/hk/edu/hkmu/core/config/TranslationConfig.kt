package hk.edu.hkmu.core.config

/**
 * Configuration for a translation engine.
 *
 * @property runtime Shared runtime knobs (accelerator, context window).
 * @property sourceLanguage Language of the input text.
 * @property targetLanguage Language of the translated text.
 * @property scriptConversion When true, apply OpenCC script conversion (e.g. zh-Hant to zh-Hans)
 *   after translation.
 */
data class TranslationConfig(
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val sourceLanguage: Language = Language.English,
    val targetLanguage: Language = Language.Mandarin,
    val scriptConversion: Boolean = true
)
