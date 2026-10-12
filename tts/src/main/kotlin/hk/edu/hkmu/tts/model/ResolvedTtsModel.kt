package hk.edu.hkmu.tts.model

import java.io.File

/**
 * A [TtsModelSpec] whose assets have been located on disk and verified to exist.
 *
 * Produced only by `ModelAssetValidator`, so every path here is guaranteed present. The engine
 * hands this to the native adapter; nothing is guessed at the native boundary.
 *
 * @property spec The originating declaration.
 * @property directory The resolved model directory.
 * @property modelPath Resolved acoustic-model `.onnx` file.
 * @property tokensPath Resolved `tokens.txt`.
 * @property lexiconPath Resolved lexicon, or `null` when the voice does not use one.
 * @property ruleFstPath Resolved rule FST, or `null`.
 * @property dataDirPath Resolved espeak-ng data dir, or `null`.
 * @property dictDirPath Resolved dictionary dir, or `null`.
 */
data class ResolvedTtsModel(
    val spec: TtsModelSpec,
    val directory: File,
    val modelPath: File,
    val tokensPath: File,
    val lexiconPath: File?,
    val ruleFstPath: File?,
    val dataDirPath: File?,
    val dictDirPath: File?
)
