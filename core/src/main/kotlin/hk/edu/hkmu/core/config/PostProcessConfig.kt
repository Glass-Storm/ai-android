package hk.edu.hkmu.core.config

/**
 * Configuration for the recombination post-processor.
 *
 * @property recombinationEnabled Whether LLM recombination runs. **Default ON.** When false the
 *   post-processor bypasses the (lazily loaded) LLM and streams the input text unchanged while
 *   preserving the streaming shape.
 * @property runtime Shared runtime knobs (accelerator, context window).
 * @property language Language the post-processor should produce.
 * @property maxInputChars Hard input cap; longer input raises an `InputTooLong` error.
 */
data class PostProcessConfig(
    val recombinationEnabled: Boolean = true,
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val language: Language = Language.Mandarin,
    val maxInputChars: Int = DEFAULT_POST_PROCESS_MAX_INPUT_CHARS
)
