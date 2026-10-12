package hk.edu.hkmu.asr

/**
 * Tier A fake for the [SenseVoiceRecognizer] seam.
 *
 * Returns canned texts/language tags in call order and records every invocation, so the module's
 * Flow wiring and segmentation can be asserted without any native library or model.
 */
internal class FakeSenseVoiceRecognizer(
    private val texts: List<String> = emptyList(),
    private val detectedTags: List<String?> = emptyList()
) : SenseVoiceRecognizer {

    /** Sample count of each received utterance, in call order. */
    val recognizedSampleCounts: MutableList<Int> = mutableListOf()

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    private var nextIndex = 0

    override fun recognize(samples: FloatArray, sampleRateHz: Int): SenseVoiceResult {
        recognizedSampleCounts += samples.size
        val text = texts.getOrElse(nextIndex) { "" }
        val tag = detectedTags.getOrElse(nextIndex) { null }
        nextIndex++
        return SenseVoiceResult(text, tag)
    }

    override fun close() {
        closed = true
    }
}
