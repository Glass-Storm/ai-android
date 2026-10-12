package hk.edu.hkmu.tts.asset

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.tts.model.ResolvedTtsModel
import hk.edu.hkmu.tts.model.TtsModelSpec
import java.io.File

/**
 * Verifies that every asset a voice needs actually exists **before** any native call.
 *
 * This exists because sherpa-onnx native does **not** validate its inputs: a missing `tokens.txt`
 * makes `generate()` silently return zero samples, and a missing lexicon can hard-exit the process.
 * Pre-validation converts all of that into the typed, catchable
 * [AiAndroidException.ModelAssetMissing] with the exact missing file name.
 *
 * @property probe File-system seam (defaults to the real file system).
 */
class ModelAssetValidator(private val probe: AssetProbe = FileSystemAssetProbe) {

    /**
     * Resolves [spec] under [modelRoot] and returns concrete paths.
     *
     * @throws AiAndroidException.ModelAssetMissing when any required asset is absent.
     */
    fun validate(spec: TtsModelSpec, modelRoot: File): ResolvedTtsModel {
        val directory = File(modelRoot, spec.directoryName)
        val names = probe.children(directory).map { it.name }.toSet()
        return ResolvedTtsModel(
            spec = spec,
            directory = directory,
            modelPath = resolveModel(spec, directory, names),
            tokensPath = requireFile(spec, directory, names, spec.tokensFile),
            lexiconPath = spec.lexiconFile?.let { requireFile(spec, directory, names, it) },
            ruleFstPath = spec.ruleFstFile?.let { requireFile(spec, directory, names, it) },
            dataDirPath = spec.dataDir?.let { requireFile(spec, directory, names, it) },
            dictDirPath = spec.dictDir?.let { requireFile(spec, directory, names, it) }
        )
    }

    private fun resolveModel(spec: TtsModelSpec, directory: File, names: Set<String>): File {
        val explicit = spec.onnxFile
        if (explicit != null) {
            return requireFile(spec, directory, names, explicit)
        }
        val matched = names.firstOrNull { it.startsWith(MODEL_PREFIX) && it.endsWith(ONNX_SUFFIX) }
            ?: throw AiAndroidException.ModelAssetMissing(spec.id, MODEL_GLOB)
        return File(directory, matched)
    }

    private fun requireFile(spec: TtsModelSpec, directory: File, names: Set<String>, name: String): File {
        if (name !in names) {
            throw AiAndroidException.ModelAssetMissing(spec.id, name)
        }
        return File(directory, name)
    }

    private companion object {
        const val MODEL_PREFIX = "model"
        const val ONNX_SUFFIX = ".onnx"
        const val MODEL_GLOB = "model*.onnx"
    }
}
