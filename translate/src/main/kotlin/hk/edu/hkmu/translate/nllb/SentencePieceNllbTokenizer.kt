package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Production [NllbTokenizer] built from the model's SentencePiece model and `tokenizer_config.json`.
 *
 * The language ids are read from `tokenizer_config.json`'s `added_tokens_decoder`, never from the
 * model card (the venddair README's `zho_Hans=256167` is wrong; the config's `256200` is correct).
 *
 * The SentencePiece engine is [SentencePieceBpe], a pure-Kotlin BPE that parses `sentencepiece.bpe.model`
 * and runs the merge itself. There is no JNI, no native library and no Android API, so this tokenizer
 * runs identically on the host JVM and on Android.
 */
internal class SentencePieceNllbTokenizer(
    sentencePieceFile: File,
    tokenizerConfigFile: File
) : NllbTokenizer {

    private var model: SentencePieceBpe? = SentencePieceBpe.loadModel(sentencePieceFile)
    private val idsByCode: Map<String, Int> = parseLanguageIds(tokenizerConfigFile)

    private val bpe: SentencePieceBpe
        get() = requireNotNull(model) { "SentencePieceNllbTokenizer is closed" }

    override fun languageId(floresCode: String): Int =
        idsByCode[floresCode] ?: throw AiAndroidException.UnsupportedLanguage(Language(floresCode))

    override fun encodeToPieceIds(text: String): IntArray = bpe.encode(text)

    override fun decodeFromPieceIds(pieceIds: IntArray): String = bpe.decode(pieceIds)

    /** Drops the parsed model. Idempotent; there is no native memory to release. */
    override fun close() {
        model = null
    }
}

/**
 * Builds the production tokenizer, validating the model directory against [NllbModelAssets] before it
 * parses the SentencePiece model.
 */
internal class SentencePieceNllbTokenizerFactory(
    private val modelId: String,
    private val modelDir: File
) : NllbTokenizerFactory {
    override fun create(): NllbTokenizer {
        val assets = NllbModelAssets.ensurePresent(modelId, modelDir)
        return SentencePieceNllbTokenizer(assets.sentencePieceFile, assets.tokenizerConfigFile)
    }
}

/** Parses `added_tokens_decoder` from `tokenizer_config.json` into `content -> NLLB id`. */
private fun parseLanguageIds(configFile: File): Map<String, Int> {
    val config = configFile.readText()
    return ADDED_TOKEN_ENTRY.findAll(config).associate { match ->
        match.groupValues[2] to match.groupValues[1].toInt()
    }
}

private val ADDED_TOKEN_ENTRY = Regex("\"(\\d+)\"\\s*:\\s*\\{\\s*\"content\"\\s*:\\s*\"([^\"]*)\"")
