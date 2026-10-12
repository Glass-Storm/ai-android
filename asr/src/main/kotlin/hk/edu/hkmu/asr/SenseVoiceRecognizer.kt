package hk.edu.hkmu.asr

/**
 * Narrow internal seam over a SenseVoice recognizer.
 *
 * [SenseVoiceAsrEngine] only depends on this interface, never on the sherpa-onnx JNI types
 * directly. Tier A (host JVM) tests substitute a fake implementation so the module's Flow wiring,
 * segmentation and ordering can be exercised without loading any native library or model.
 *
 * The production implementation is constructed by [NativeSenseVoiceRecognizerFactory], which
 * pre-validates the model directory ([AsrModelAssets]) before it touches native memory.
 */
internal interface SenseVoiceRecognizer : AutoCloseable {

    /**
     * Recognizes one whole utterance of 16 kHz mono PCM (normalized Float32 in `[-1, 1]`).
     *
     * This is an offline, whole-segment call: it returns exactly one final text for [samples] and
     * never emits partial/interim hypotheses.
     */
    fun recognize(samples: FloatArray, sampleRateHz: Int): SenseVoiceResult

    /** Releases the underlying native recognizer. Idempotent. */
    override fun close() {}
}

/**
 * Result of a single whole-utterance recognition.
 *
 * @property text Final recognized text (may be blank for non-speech audio).
 * @property detectedLanguageTag Language tag reported by the model (`en`/`yue`/`zh`/`ja`/`ko`),
 *   or null when the model does not report one.
 */
internal data class SenseVoiceResult(
    val text: String,
    val detectedLanguageTag: String? = null
)
