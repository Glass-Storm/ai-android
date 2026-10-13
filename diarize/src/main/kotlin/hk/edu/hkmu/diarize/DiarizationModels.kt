package hk.edu.hkmu.diarize

/**
 * Declarative description of the two ONNX graphs a diarization run needs, independent of where the
 * files live.
 *
 * Paths are resolved under the caller's `modelDir`:
 * - `modelDir/[segmentationDirectory]/[segmentationFile]` — the pyannote voice-activity /
 *   speaker-change segmenter.
 * - `modelDir/[embeddingFile]` — the speaker-embedding extractor.
 *
 * @property id Stable model id, also the value reported in `AiAndroidException.ModelAssetMissing`.
 * @property segmentationDirectory Directory (under the caller's model root) holding the segmenter.
 * @property segmentationFile Segmentation graph file name inside [segmentationDirectory].
 * @property embeddingFile Speaker-embedding graph file name directly under the model root.
 */
data class DiarizationModelSpec(
    val id: String,
    val segmentationDirectory: String,
    val segmentationFile: String,
    val embeddingFile: String
)

/**
 * The model files the diarization engine expects, with the exact download URLs and sha256 pins.
 *
 * ### Layout ([Pyannote3Dspeaker])
 * ```
 * <modelDir>/
 *   sherpa-onnx-pyannote-segmentation-3-0/
 *     model.int8.onnx                       # segmentation
 *   3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx   # embedding
 * ```
 * Only those two `.onnx` files are required; the rest of the extracted segmentation tarball is
 * unused.
 *
 * ### Segmentation — pyannote/segmentation-3.0 (int8), MIT (© 2022 CNRS)
 * - Bundle URL (base + asset):
 *   `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/`
 *   + `sherpa-onnx-pyannote-segmentation-3-0.tar.bz2`
 * - Bundle sha256 `24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488`
 * - Inner `sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx`
 *   sha256 `d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d`
 * - Inner `sherpa-onnx-pyannote-segmentation-3-0/model.onnx` (float32, larger)
 *   sha256 `220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079`
 *
 * ### Embedding — 3D-Speaker CAM++ zh-cn (Apache-2.0)
 * - URL (base + asset):
 *   `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/`
 *   + `3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`
 * - sha256 `f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11`
 *
 * Every URL and sha256 above was verified against the downloaded bytes. Run
 * `scripts/fetch-diarization-models.sh <modelDir>` to fetch and verify them into the expected layout.
 */
object DiarizationModels {

    /** Directory (under the model root) that the pyannote segmentation bundle extracts to. */
    const val PYANNOTE_SEGMENTATION_DIRECTORY: String = "sherpa-onnx-pyannote-segmentation-3-0"

    /** int8 segmentation graph inside [PYANNOTE_SEGMENTATION_DIRECTORY]. */
    const val PYANNOTE_SEGMENTATION_FILE: String = "model.int8.onnx"

    /** 3D-Speaker CAM++ speaker-embedding graph, placed directly under the model root. */
    const val EMBEDDING_FILE: String = "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"

    /** Stable id of the recommended segmentation + embedding pair. */
    const val PYANNOTE_3DSPEAKER_ID: String = "sherpa-onnx-pyannote-3dspeaker-zh"

    /** Recommended spec: pyannote/segmentation-3.0 (int8) + 3D-Speaker CAM++ zh-cn embedding. */
    val Pyannote3Dspeaker: DiarizationModelSpec = DiarizationModelSpec(
        id = PYANNOTE_3DSPEAKER_ID,
        segmentationDirectory = PYANNOTE_SEGMENTATION_DIRECTORY,
        segmentationFile = PYANNOTE_SEGMENTATION_FILE,
        embeddingFile = EMBEDDING_FILE
    )
}
