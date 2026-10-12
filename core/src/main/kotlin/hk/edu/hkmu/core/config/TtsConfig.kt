package hk.edu.hkmu.core.config

/**
 * Configuration for a TTS engine.
 *
 * @property runtime Shared runtime knobs (accelerator, context window).
 * @property language Synthesis language.
 * @property sampleRateHz Sample rate (Hz) of the produced PCM audio.
 * @property speakerId Multi-speaker model id; [DEFAULT_SPEAKER_ID] for single-speaker models.
 */
data class TtsConfig(
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val language: Language = Language.Mandarin,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val speakerId: Int = DEFAULT_SPEAKER_ID
)
