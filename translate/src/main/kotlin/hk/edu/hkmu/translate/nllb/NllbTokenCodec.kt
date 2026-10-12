package hk.edu.hkmu.translate.nllb

/**
 * Pure NLLB token-layout helpers for the fairseq id offset and special-token conventions.
 *
 * The ONNX graphs were exported with `fairseq_offset = 1`, so a raw SentencePiece id `p` maps to HF
 * id `p + 1`, with the three SentencePiece specials remapped (`<unk>`->3, `<s>`->0, `</s>`->2). The
 * encoder input is `[src_lang_id] ++ toHfId(pieces) ++ [eos]`; the decoder is seeded
 * `[bos, tgt_lang_id]` (seeding with only the target language id decodes degenerately).
 */
internal object NllbTokenCodec {
    /** NLLB HF id of `</s>`, reused as the decoder BOS seed and as end-of-sequence. */
    const val BOS: Int = 2

    /** NLLB HF id of `</s>` (end of sequence). */
    const val EOS: Int = 2

    /** NLLB HF id of `<unk>`. */
    const val UNK: Int = 3

    /** Highest reserved HF id; ids above this are real sub-word pieces. */
    const val SPECIAL_MAX: Int = 3

    private const val SP_UNK = 0
    private const val SP_BOS = 1
    private const val SP_EOS = 2

    /** Maps a raw SentencePiece id to its NLLB HF id. */
    fun toHfId(spId: Int): Int = when (spId) {
        SP_UNK -> UNK
        SP_BOS -> 0
        SP_EOS -> EOS
        else -> spId + 1
    }

    /** Builds the encoder `input_ids`: `[sourceLangId] ++ toHfId(pieces) ++ [eos]`. */
    fun buildSourceInput(sourceLangId: Int, pieces: IntArray): LongArray {
        val input = LongArray(pieces.size + 2)
        input[0] = sourceLangId.toLong()
        pieces.forEachIndexed { index, spId -> input[index + 1] = toHfId(spId).toLong() }
        input[input.size - 1] = EOS.toLong()
        return input
    }

    /** Drops specials and undoes the +1 offset, returning raw SentencePiece piece ids. */
    fun toPieceIds(generatedHfIds: List<Int>): IntArray =
        generatedHfIds.filter { it > SPECIAL_MAX }.map { it - 1 }.toIntArray()
}
