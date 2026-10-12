package hk.edu.hkmu.core.config

/**
 * Runtime knobs shared by every engine.
 *
 * @property contextWindowTokens Maximum number of tokens the autoregressive stage may attend to.
 *   **This is reload-costed:** changing it invalidates any native context/graph sized for the
 *   previous window, so the engine must release and re-create it. Treat it as a load-time
 *   parameter and do not toggle it per call.
 * @property accelerator Preferred hardware backend; see [Accelerator].
 */
data class EngineRuntimeConfig(
    val contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    val accelerator: Accelerator = Accelerator.Auto
)
