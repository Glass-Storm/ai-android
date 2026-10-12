package hk.edu.hkmu.translate.nllb

/**
 * Narrow internal seam over the NLLB text tokenizer.
 *
 * [NllbTranslationEngine] depends only on this interface, never on DJL/SentencePiece JNI types, so
 * Tier A (host JVM) tests substitute a fake and exercise the whole encode -> decode pipeline without
 * any model. The production implementation is built by [SentencePieceNllbTokenizerFactory].
 */
internal interface NllbTokenizer : AutoCloseable {
    /** @return the NLLB HF id of the added language token [floresCode]. */
    fun languageId(floresCode: String): Int

    /** Encodes [text] to raw SentencePiece piece ids. */
    fun encodeToPieceIds(text: String): IntArray

    /** Decodes raw SentencePiece piece ids back to text. */
    fun decodeFromPieceIds(pieceIds: IntArray): String

    /** Releases native resources. Idempotent. */
    override fun close() {}
}

/** Builds the production [NllbTokenizer] lazily on first use. */
internal fun interface NllbTokenizerFactory {
    fun create(): NllbTokenizer
}
