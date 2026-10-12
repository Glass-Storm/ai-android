package hk.edu.hkmu.tts.model

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException

/** Cantonese VITS voice (`csukuangfj/vits-cantonese-hf-xiaomaiiwn`). Native rate is 22050 Hz. */
val CantoneseVits: TtsModelSpec = TtsModelSpec(
    id = "vits-cantonese-hf-xiaomaiiwn",
    language = Language.Cantonese,
    directoryName = "vits-cantonese-hf-xiaomaiiwn",
    onnxFile = "vits-cantonese-hf-xiaomaiiwn.onnx",
    lexiconFile = "lexicon.txt",
    ruleFstFile = "rule.fst",
    sampleRateHintHz = CANTONESE_SAMPLE_RATE_HINT_HZ
)

/** Mandarin VITS voice. Ships `model*.onnx`, `tokens.txt` and a `lexicon.txt`. */
val MandarinVits: TtsModelSpec = TtsModelSpec(
    id = "vits-zh",
    language = Language.Mandarin,
    directoryName = "vits-zh",
    lexiconFile = "lexicon.txt"
)

/** English Piper VITS voice. Uses espeak-ng data rather than a lexicon. */
val EnglishPiper: TtsModelSpec = TtsModelSpec(
    id = "vits-piper-en",
    language = Language.English,
    directoryName = "vits-piper-en",
    dataDir = "espeak-ng-data"
)

/**
 * Maps a [Language] to the [TtsModelSpec] that serves it.
 *
 * The catalog is data, not code: hosts can supply their own list (e.g. a pre-provisioned layout)
 * and the engine still resolves languages the same way. Unknown languages fail with the typed
 * `UnsupportedLanguage` before any asset or native work happens.
 *
 * @property specs Declared voices; the first match for a language wins.
 */
class TtsModelCatalog(private val specs: List<TtsModelSpec> = defaultSpecs()) {

    /** The voice serving [language], or `null` when unsupported. */
    fun forLanguage(language: Language): TtsModelSpec? = specs.firstOrNull { it.language == language }

    /** The voice serving [language]; throws typed `UnsupportedLanguage` when absent. */
    fun requireLanguage(language: Language): TtsModelSpec =
        forLanguage(language) ?: throw AiAndroidException.UnsupportedLanguage(language)

    /** Every declared voice, in declaration order. */
    fun all(): List<TtsModelSpec> = specs

    companion object {
        /** Catalog with the three shipped voices (Cantonese, Mandarin, English). */
        val Standard: TtsModelCatalog = TtsModelCatalog()

        /** The shipped voice list. */
        fun defaultSpecs(): List<TtsModelSpec> = listOf(CantoneseVits, MandarinVits, EnglishPiper)
    }
}

/** Documentation-only Cantonese native rate; the loaded model remains authoritative. */
private const val CANTONESE_SAMPLE_RATE_HINT_HZ = 22_050
