package hk.edu.hkmu.translate.nllb

/**
 * Tier A fake for the [NllbSession] seam.
 *
 * Records the encoder input and the decoder seed of the first step, then returns [scriptedTokens] in
 * order followed by `</s>`, so the engine's two-pass wiring, seeding and stop condition can be
 * asserted without ONNX.
 */
internal class FakeNllbSession(
    private val scriptedTokens: List<Int> = emptyList()
) : NllbSession {

    /** Encoder `input_ids` of each [encode] call, in call order. */
    val encodedInputs: MutableList<LongArray> = mutableListOf()

    /** The decoder prefix at the first decode step of each call. */
    val decoderSeeds: MutableList<List<Long>> = mutableListOf()

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    private var step = 0

    override fun encode(inputIds: LongArray, attentionMask: LongArray): EncodedSource {
        encodedInputs += inputIds.copyOf()
        return EncodedSource(inputIds.size)
    }

    override fun nextToken(decoderIds: LongArray, source: EncodedSource): Int {
        if (step == 0) {
            decoderSeeds += decoderIds.toList()
        }
        val next = scriptedTokens.getOrElse(step) { NllbTokenCodec.EOS }
        step++
        return next
    }

    override fun close() {
        closed = true
    }
}
