package hk.edu.hkmu.diarize

/**
 * Narrow internal seam over a speaker-embedding backend.
 *
 * [SherpaSpeakerEmbeddingExtractor] depends only on this interface, never on the sherpa-onnx JNI
 * types directly. Tier A (host JVM) tests substitute a fake so the module's Flow wiring and license
 * gate can be exercised without loading any native library or model.
 */
internal interface SpeakerEmbeddingBackend : AutoCloseable {

    /**
     * Computes the speaker-embedding vector for one full utterance of 16 kHz mono PCM (normalized
     * Float32 in `[-1, 1]`).
     *
     * This is an offline, whole-utterance call: it returns the embedding for the whole input at once.
     */
    fun embed(samples: FloatArray): FloatArray

    /** Releases the underlying native session. Idempotent. */
    override fun close() {}
}

/** Constructs the production [SpeakerEmbeddingBackend] from a resolved model directory. */
internal fun interface SpeakerEmbeddingBackendFactory {
    /** Creates the backend; called at load, never at engine construction. */
    fun create(): SpeakerEmbeddingBackend
}
