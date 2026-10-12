package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Required-file validation for a sign classifier artifact, run **before** any native TFLite call.
 *
 * LiteRT's `Interpreter(file, ...)` fails deep in native when the model file is absent; resolving it
 * up front turns a quiet "file not there" into a typed [AiAndroidException.ModelAssetMissing] from
 * pure JVM code, so it is fully unit-testable on the host.
 */
internal object SignModelAssets {

    /**
     * Resolves and validates `[modelDir]/[modelFile]`.
     *
     * @return the artifact file when present.
     * @throws AiAndroidException.ModelAssetMissing when [modelDir] is not a directory or the artifact
     *   file is absent.
     */
    fun ensurePresent(modelId: String, modelDir: File, modelFile: String): File {
        if (!modelDir.isDirectory) {
            throw AiAndroidException.ModelAssetMissing(modelId, modelDir.path)
        }
        val file = File(modelDir, modelFile)
        if (!file.isFile) {
            throw AiAndroidException.ModelAssetMissing(modelId, modelFile)
        }
        return file
    }
}
