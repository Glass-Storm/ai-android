package hk.edu.hkmu.core.config

/**
 * Configuration for a sign-language engine.
 *
 * @property classifierId Registry id of the pluggable sign classifier.
 * @property runtime Shared runtime knobs (accelerator, context window).
 * @property windowSize Number of landmark frames per recognition window.
 * @property stride Number of frames advanced between consecutive windows.
 * @property language Sign language served by [classifierId] (e.g. ASL or CSL).
 */
data class SignConfig(
    val classifierId: String,
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val windowSize: Int = DEFAULT_SIGN_WINDOW_SIZE,
    val stride: Int = DEFAULT_SIGN_STRIDE,
    val language: Language = Language.English
)
