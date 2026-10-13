package hk.edu.hkmu.diarize

/**
 * Tier A fake for the [SpeakerEmbeddingBackend] seam.
 *
 * Returns a canned [embedding] and records every call and `close`, so the module's Flow wiring and
 * lifecycle can be asserted without any native library or model.
 */
internal class FakeSpeakerEmbeddingBackend(
    private val embedding: FloatArray = DEFAULT_EMBEDDING
) : SpeakerEmbeddingBackend {

    /** Sample count of each received call, in call order. */
    val embedCallSampleCounts: MutableList<Int> = mutableListOf()

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    override fun embed(samples: FloatArray): FloatArray {
        embedCallSampleCounts += samples.size
        return embedding
    }

    override fun close() {
        closed = true
    }

    companion object {
        /** Deterministic non-empty voiceprint for tests. */
        val DEFAULT_EMBEDDING: FloatArray = floatArrayOf(0.1f, 0.2f, 0.3f)
    }
}
