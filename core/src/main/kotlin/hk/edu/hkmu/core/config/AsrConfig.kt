package hk.edu.hkmu.core.config

/**
 * Configuration for an ASR engine.
 *
 * @property runtime Shared runtime knobs (accelerator, context window).
 * @property language Recognition language, or [Language.Auto] for auto-detection.
 * @property sampleRateHz Expected sample rate (Hz) of incoming PCM audio.
 */
data class AsrConfig(
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val language: Language = Language.Auto,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
)
