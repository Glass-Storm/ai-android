package hk.edu.hkmu.asr

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Absolute paths to the model assets required to construct the native recognizer.
 */
internal class ResolvedAsrAssets(
    val modelFile: File,
    val tokensFile: File
)

/**
 * Required-file manifest for the SenseVoice model directory, validated **before** any native call.
 *
 * sherpa-onnx native does not validate its asset paths: a missing `tokens.txt` yields an opaque
 * `_Map_base::at` at decode time and a missing model can hard-exit the process (Wave-0 S4). This
 * object is the single place that turns "asset quietly absent" into a typed
 * [AiAndroidException.ModelAssetMissing] raised from pure JVM code, so it is also fully unit-testable
 * on the host.
 */
internal object AsrModelAssets {

    /** Glob matching the SenseVoice ONNX graph (e.g. `model.int8.onnx`). */
    const val MODEL_GLOB: String = "model*.onnx"

    /** Token table required by the recognizer. */
    const val TOKENS_FILE: String = "tokens.txt"

    private val requiredAssets: List<String> = listOf(MODEL_GLOB, TOKENS_FILE)

    /**
     * Resolves the required assets under [modelDir].
     *
     * @throws AiAndroidException.ModelAssetMissing when [modelDir] is not a directory or any
     *   required asset is absent. Never touches native memory.
     */
    fun ensurePresent(modelId: String, modelDir: File): ResolvedAsrAssets {
        if (!modelDir.isDirectory) {
            throw AiAndroidException.ModelAssetMissing(modelId, modelDir.path)
        }
        requiredAssets.forEach { resolveRequired(modelId, modelDir, it) }
        return ResolvedAsrAssets(
            modelFile = resolveRequired(modelId, modelDir, MODEL_GLOB),
            tokensFile = resolveRequired(modelId, modelDir, TOKENS_FILE)
        )
    }

    private fun resolveRequired(modelId: String, modelDir: File, glob: String): File = findFirst(modelDir, glob)
        ?: throw AiAndroidException.ModelAssetMissing(modelId, glob)

    private fun findFirst(dir: File, glob: String): File? {
        val regex = globToRegex(glob)
        return dir.listFiles()?.firstOrNull { it.isFile && regex.matches(it.name) }
    }

    private fun globToRegex(glob: String): Regex {
        val body = glob.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$body$")
    }
}
