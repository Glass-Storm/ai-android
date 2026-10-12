package hk.edu.hkmu.translate.nllb

/**
 * Encoder output for one source sequence.
 *
 * @property length Source sequence length `S`.
 * @property handle Opaque production handle (the ONNX tensors reused by every decoder step); null
 *   for a fake. Closing the [EncodedSource] releases it.
 */
internal class EncodedSource(
    val length: Int,
    internal val handle: Any? = null
) : AutoCloseable {
    override fun close() {
        (handle as? AutoCloseable)?.close()
    }
}

/**
 * Narrow internal seam over the two NLLB ONNX graphs (encoder + decoder).
 *
 * The engine runs the encoder once per source, then calls [nextToken] repeatedly for the non-cached
 * greedy decode. Confining all `ai.onnxruntime` types to the production implementation keeps Tier A
 * tests free of native libraries and of the real model.
 */
internal interface NllbSession : AutoCloseable {
    /** Runs the encoder over `[1,S]` id tensors and returns the source encoding. */
    fun encode(inputIds: LongArray, attentionMask: LongArray): EncodedSource

    /**
     * Runs one non-cached decoder step; returns the greedy (argmax) next token id for the full
     * prefix [decoderIds].
     */
    fun nextToken(decoderIds: LongArray, source: EncodedSource): Int

    /** Releases both native sessions. Idempotent. */
    override fun close() {}
}

/** Builds the production [NllbSession] lazily on first use. */
internal fun interface NllbSessionFactory {
    fun create(): NllbSession
}
