package hk.edu.hkmu.core.config

/** Default audio sample rate (Hz) for ASR input and TTS output unless overridden. */
const val DEFAULT_SAMPLE_RATE_HZ: Int = 16_000

/** Default TTS speaker id used by single-speaker models. */
const val DEFAULT_SPEAKER_ID: Int = 0

/** Default autoregressive context window (tokens) used when the caller does not override it. */
const val DEFAULT_CONTEXT_WINDOW_TOKENS: Int = 512

/** Default number of landmark frames per sign-recognition window. */
const val DEFAULT_SIGN_WINDOW_SIZE: Int = 30

/** Default number of frames advanced between consecutive sign-recognition windows. */
const val DEFAULT_SIGN_STRIDE: Int = 10

/** Default maximum number of characters accepted by the recombination post-processor. */
const val DEFAULT_POST_PROCESS_MAX_INPUT_CHARS: Int = 512
