package hk.edu.hkmu.translate.nllb

import ai.djl.sentencepiece.SpProcessor
import ai.djl.sentencepiece.SpTokenizer
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Production [NllbTokenizer] built from the model's SentencePiece model and `tokenizer_config.json`.
 *
 * The language ids are read from `tokenizer_config.json`'s `added_tokens_decoder`, never from the
 * model card (the venddair README's `zho_Hans=256167` is wrong; the config's `256200` is correct).
 *
 * Android note: DJL `ai.djl.sentencepiece` ships natives only for linux/macOS/Windows — there is no
 * Android build, so this implementation runs on the host JVM (the path the Wave-0 S1 recipe
 * verified). The Android production path must resolve a `tokenizer.json` through
 * `ai.djl.android:tokenizer-native` instead; see the task-15 evidence and notepad.
 */
internal class SentencePieceNllbTokenizer(
    sentencePieceFile: File,
    tokenizerConfigFile: File
) : NllbTokenizer {

    private val tokenizer: SpTokenizer = SpTokenizer(sentencePieceFile.toPath())
    private val processor: SpProcessor = tokenizer.processor
    private val idsByCode: Map<String, Int> = parseLanguageIds(tokenizerConfigFile)

    override fun languageId(floresCode: String): Int =
        idsByCode[floresCode] ?: throw AiAndroidException.UnsupportedLanguage(Language(floresCode))

    override fun encodeToPieceIds(text: String): IntArray = processor.encode(text)

    override fun decodeFromPieceIds(pieceIds: IntArray): String = processor.decode(pieceIds)

    override fun close() = tokenizer.close()
}

/**
 * Builds the production tokenizer, validating the model directory against [NllbModelAssets] before
 * it touches the SentencePiece native library.
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
