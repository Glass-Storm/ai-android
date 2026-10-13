package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/** Absolute paths to the model assets required to construct the native diarizer. */
internal class ResolvedDiarizationAssets(
    val segmentationFile: File,
    val embeddingFile: File
)

/**
 * Required-file manifest for a diarization model layout, validated **before** any native call.
 *
 * sherpa-onnx native does not validate its asset paths: a missing graph surfaces as an opaque native
 * failure (or a hard exit) rather than a typed error. This object turns "asset quietly absent" into a
 * typed [AiAndroidException.ModelAssetMissing] raised from pure JVM code, so it is also fully
 * unit-testable on the host.
 */
internal object DiarizationModelAssets {

    /**
     * Resolves the segmentation and embedding graphs for `spec` under `modelDir`.
     *
     * @throws AiAndroidException.ModelAssetMissing when `modelDir` is not a directory or either graph
     *   is absent. Never touches native memory.
     */
    fun ensurePresent(spec: DiarizationModelSpec, modelDir: File): ResolvedDiarizationAssets {
        if (!modelDir.isDirectory) {
            throw AiAndroidException.ModelAssetMissing(spec.id, modelDir.path)
        }
        return ResolvedDiarizationAssets(
            segmentationFile = requireFile(spec, File(modelDir, spec.segmentationDirectory), spec.segmentationFile),
            embeddingFile = requireFile(spec, modelDir, spec.embeddingFile)
        )
    }

    private fun requireFile(spec: DiarizationModelSpec, directory: File, name: String): File {
        val file = File(directory, name)
        if (!file.isFile) {
            throw AiAndroidException.ModelAssetMissing(spec.id, name)
        }
        return file
    }
}
