package hk.edu.hkmu.core.di

/**
 * A pipeline stage slot in an assembled [AiAndroidGraph].
 *
 * The slot is the graph's key and the unit of "is this engine present?". Each constant carries the
 * public engine interface name so a missing stage can be reported by
 * `AiAndroidException.MissingDependency` with a stable, human-meaningful name (the engine contract),
 * not an internal implementation detail.
 *
 * @property engineName Public interface name of the engine that fills this slot.
 */
enum class EngineSlot(val engineName: String) {
    /** Speech-to-text slot; filled by an `AsrEngine`. */
    Asr("AsrEngine"),

    /** Text-to-speech slot; filled by a `TtsEngine`. */
    Tts("TtsEngine"),

    /** Translation slot; filled by a `TranslationEngine`. */
    Translation("TranslationEngine"),

    /** Sign-language slot; filled by a `SignEngine`. */
    Sign("SignEngine"),

    /** Recombination slot; filled by a `PostProcessor`. */
    PostProcess("PostProcessor")
}
