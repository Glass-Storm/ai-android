package hk.edu.hkmu.recombine

/**
 * Sampling knobs for the recombination LLM.
 *
 * @property maxTokens Pieces generated per [LlamaPostProcessor.process] call.
 * @property seed Sampler seed; only consulted when [temperature] is positive.
 * @property temperature `<= 0` (the default) selects deterministic greedy decoding, which is
 *   seed-independent and byte-reproducible. A positive value selects a seeded temperature+dist
 *   sampler chain, reproducible for a fixed [seed].
 */
data class LlamaSamplingConfig(
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val seed: Int = DEFAULT_SEED,
    val temperature: Float = DEFAULT_TEMPERATURE
) {
    companion object {
        /** Pieces generated per call by default. */
        const val DEFAULT_MAX_TOKENS: Int = 128

        /** Default sampler seed. */
        const val DEFAULT_SEED: Int = 42

        /** Default temperature: greedy, deterministic. */
        const val DEFAULT_TEMPERATURE: Float = 0.0f
    }
}
