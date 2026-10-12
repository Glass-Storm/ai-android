package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/** Absolute paths to the model assets required to construct the NLLB tokenizer and sessions. */
internal class ResolvedNllbAssets(
    val encoderFile: File,
    val decoderFile: File,
    val sentencePieceFile: File,
    val tokenizerConfigFile: File
)

/**
 * Required-file manifest for the NLLB INT8 model directory, validated before any native/ONNX call.
 *
 * A missing graph or tokenizer asset otherwise surfaces as an opaque ONNX load failure deep in the
 * session; resolving the required files up front turns that into a typed
 * [AiAndroidException.ModelAssetMissing] from pure JVM code, so it is fully unit-testable on the
 * host and a host fails fast on a half-provisioned model directory.
 */
internal object NllbModelAssets {
    /** Glob matching the INT8 encoder graph (e.g. `encoder_model_int8.onnx`). */
    const val ENCODER_GLOB: String = "encoder_model*.onnx"

    /** Glob matching the INT8 decoder graph (e.g. `decoder_model_int8.onnx`). */
    const val DECODER_GLOB: String = "decoder_model*.onnx"

    /** SentencePiece model used by the tokenizer. */
    const val SENTENCEPIECE_FILE: String = "sentencepiece.bpe.model"

    /** `tokenizer_config.json` holding the added language-token ids. */
    const val TOKENIZER_CONFIG_FILE: String = "tokenizer_config.json"

    /**
     * Resolves the required assets under [modelDir].
     *
     * @throws AiAndroidException.ModelAssetMissing when [modelDir] is not a directory or any
     *   required asset is absent. Never touches native memory.
     */
    fun ensurePresent(modelId: String, modelDir: File): ResolvedNllbAssets {
        if (!modelDir.isDirectory) {
            throw AiAndroidException.ModelAssetMissing(modelId, modelDir.path)
        }
        return ResolvedNllbAssets(
            encoderFile = resolveRequired(modelId, modelDir, ENCODER_GLOB),
            decoderFile = resolveRequired(modelId, modelDir, DECODER_GLOB),
            sentencePieceFile = resolveRequired(modelId, modelDir, SENTENCEPIECE_FILE),
            tokenizerConfigFile = resolveRequired(modelId, modelDir, TOKENIZER_CONFIG_FILE)
        )
    }

    private fun resolveRequired(modelId: String, modelDir: File, glob: String): File =
        findFirst(modelDir, glob) ?: throw AiAndroidException.ModelAssetMissing(modelId, glob)

    private fun findFirst(dir: File, glob: String): File? {
        val regex = globToRegex(glob)
        return dir.listFiles()?.firstOrNull { it.isFile && regex.matches(it.name) }
    }

    private fun globToRegex(glob: String): Regex {
        val body = glob.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$body$")
    }
}
