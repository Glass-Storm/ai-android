package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Translates backend decisions into one engine's concrete [BackendKnob]s.
 *
 * @param K the engine's knob type.
 */
interface EngineBackendAdapter<K : BackendKnob> {
    /** Stable engine id used in diagnostics, e.g. `sherpa`. */
    val engineId: String

    /** The accelerators this engine can realize a knob for. Always contains [Accelerator.Cpu]. */
    val supportedAccelerators: Set<Accelerator>

    /**
     * @param accelerator the accelerator to realize. [Accelerator.Auto] is not expected here — the
     *   [EngineBackendPlanResolver] expands it via the `BackendSelector` —
     *   and degrades to the CPU knob when passed directly.
     * @return the concrete knob for [accelerator].
     * @throws AiAndroidException.AcceleratorUnavailable when the engine has no knob for it.
     */
    fun knobFor(accelerator: Accelerator): K
}

/** One concrete plan entry: an accelerator and the engine knob that realizes it. */
data class BackendAttempt<K : BackendKnob>(
    val accelerator: Accelerator,
    val knob: K
)

/**
 * An ordered, deterministic set of attempts for one engine.
 *
 * [attempts] is never empty and always begins with the primary choice; each attempt's
 * [BackendAttempt.knob] realizes that attempt's accelerator.
 */
data class BackendPlan<K : BackendKnob>(
    val engineId: String,
    val attempts: List<BackendAttempt<K>>
) {
    init {
        require(attempts.isNotEmpty()) { "a backend plan must contain at least one attempt" }
        require(attempts.all { it.accelerator == it.knob.accelerator }) {
            "each attempt's knob must realize the attempt's accelerator"
        }
    }

    /** The primary attempt the selector decided on. */
    val chosen: BackendAttempt<K> get() = attempts.first()
}
