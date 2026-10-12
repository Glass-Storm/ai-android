package hk.edu.hkmu.tts.model

import hk.edu.hkmu.core.config.Language

/** Default token table name used by every sherpa-onnx VITS voice. */
const val DEFAULT_TOKENS_FILE: String = "tokens.txt"

/** Default ONNX Runtime intra-op thread count for a VITS graph. */
const val DEFAULT_NUM_THREADS: Int = 2

/**
 * Declarative description of one sherpa-onnx VITS voice, independent of where the files live.
 *
 * Paths are *relative to the model directory*: the engine joins them with
 * `modelRoot/[directoryName]`. Only VITS-family models are modelled here; the Kokoro path is
 * intentionally excluded (no Cantonese voice and a known JNI callback bug — see the S4 findings).
 *
 * @property id Stable model id, also the value reported in `ModelAssetMissing`.
 * @property language Language this voice serves.
 * @property directoryName Directory (under the caller's model root) holding the voice.
 * @property onnxFile Explicit acoustic-model file name, or `null` to auto-detect `model*.onnx`.
 * @property tokensFile Token table file name.
 * @property lexiconFile Lexicon file name, when the voice needs one.
 * @property ruleFstFile Text-normalization FST file name, when the voice ships one.
 * @property dataDir espeak-ng data directory name (Piper voices), when needed.
 * @property dictDir Jieba/dictionary directory name, when needed.
 * @property numThreads ONNX Runtime intra-op threads.
 * @property sampleRateHintHz Documentation-only hint of the native rate. The **authoritative**
 *   rate always comes from the loaded model (`VitsSynthesizer.sampleRateHz`); never assume it.
 */
@Suppress("LongParameterList")
data class TtsModelSpec(
    val id: String,
    val language: Language,
    val directoryName: String,
    val onnxFile: String? = null,
    val tokensFile: String = DEFAULT_TOKENS_FILE,
    val lexiconFile: String? = null,
    val ruleFstFile: String? = null,
    val dataDir: String? = null,
    val dictDir: String? = null,
    val numThreads: Int = DEFAULT_NUM_THREADS,
    val sampleRateHintHz: Int? = null
)
