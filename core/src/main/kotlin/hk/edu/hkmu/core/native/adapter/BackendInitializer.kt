package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException

/** The accelerator that was actually bound, plus the typed fallbacks skipped on the way. */
data class InitializedBackend<K : BackendKnob>(
    val attempt: BackendAttempt<K>,
    val fallbacks: List<AiAndroidException.AcceleratorUnavailable>
) {
    /** The knob that was bound into the engine. */
    val knob: K get() = attempt.knob

    /** The accelerator that was actually bound. */
    val accelerator: Accelerator get() = attempt.accelerator
}

/**
 * Deterministically initializes a [BackendPlan], falling back to the next attempt when an
 * accelerator fails to initialize.
 *
 * Attempts are tried in plan order (primary first) and an attempt succeeds when [binder] returns
 * without throwing. Two failure shapes trigger the fallback:
 *  1. the binder raises [AiAndroidException.AcceleratorUnavailable] — the engine's explicit signal
 *     that the accelerator cannot be used; or
 *  2. the binder raises any other exception for a **non-CPU** attempt — a failing GPU/accelerator
 *     path is never forced, so it is surfaced as a typed [AiAndroidException.AcceleratorUnavailable]
 *     (the original is preserved via [Throwable.addSuppressed]) before moving on.
 *
 * CPU attempts are terminal (no fallback exists), so their failure propagates unchanged. The
 * **final** attempt likewise propagates: a CPU failure is rethrown as-is and a non-CPU failure is
 * wrapped as a typed [AiAndroidException.AcceleratorUnavailable]. Initialization is deterministic —
 * the same plan and binder outcome always yield the same bound attempt and fallback list.
 */
class BackendInitializer {
    /**
     * @param plan the ordered attempts, primary first.
     * @param binder the engine binding for one knob; throws when the accelerator cannot initialize.
     * @return the bound attempt plus the typed fallbacks that preceded it.
     * @throws AiAndroidException.AcceleratorUnavailable when the final attempt is a non-CPU
     *   accelerator that fails.
     * @throws Exception when a CPU attempt fails (re-thrown unchanged).
     */
    fun <K : BackendKnob> initialize(plan: BackendPlan<K>, binder: (K) -> Unit): InitializedBackend<K> {
        val fallbacks = mutableListOf<AiAndroidException.AcceleratorUnavailable>()
        val attempts = plan.attempts
        val lastIndex = attempts.lastIndex
        val boundIndex = (0 until lastIndex).firstOrNull { index ->
            tryAttempt(plan, index, binder, fallbacks)
        }
        if (boundIndex != null) {
            return InitializedBackend(attempts[boundIndex], fallbacks.toList())
        }
        return bindFinal(plan, lastIndex, binder, fallbacks)
    }

    /** @return true when attempt [index] bound; false when it fell back; throws on a fatal CPU error. */
    @Suppress("TooGenericExceptionCaught") // a failing GPU path must never be forced
    private fun <K : BackendKnob> tryAttempt(
        plan: BackendPlan<K>,
        index: Int,
        binder: (K) -> Unit,
        fallbacks: MutableList<AiAndroidException.AcceleratorUnavailable>
    ): Boolean {
        val attempt = plan.attempts[index]
        return try {
            binder(attempt.knob)
            true
        } catch (unavailable: AiAndroidException.AcceleratorUnavailable) {
            if (attempt.accelerator == Accelerator.Cpu) throw unavailable
            fallbacks += unavailable
            false
        } catch (failure: Exception) {
            if (attempt.accelerator == Accelerator.Cpu) throw failure
            fallbacks += asAcceleratorUnavailable(plan, index, failure)
            false
        }
    }

    /** Binds the terminal attempt, wrapping a non-CPU failure as a typed error. */
    @Suppress("TooGenericExceptionCaught") // a failing GPU path must never be forced
    private fun <K : BackendKnob> bindFinal(
        plan: BackendPlan<K>,
        lastIndex: Int,
        binder: (K) -> Unit,
        fallbacks: MutableList<AiAndroidException.AcceleratorUnavailable>
    ): InitializedBackend<K> {
        val attempt = plan.attempts[lastIndex]
        try {
            binder(attempt.knob)
        } catch (failure: Exception) {
            throw asTerminalFailure(plan, lastIndex, attempt, failure)
        }
        return InitializedBackend(attempt, fallbacks.toList())
    }

    /**
     * @return the error to raise for the terminal attempt: a CPU failure and an already-typed
     *   accelerator error pass through unchanged, any other non-CPU failure becomes a typed one.
     */
    private fun <K : BackendKnob> asTerminalFailure(
        plan: BackendPlan<K>,
        lastIndex: Int,
        attempt: BackendAttempt<K>,
        failure: Exception
    ): Exception {
        val terminal = attempt.accelerator == Accelerator.Cpu ||
            failure is AiAndroidException.AcceleratorUnavailable
        return if (terminal) failure else asAcceleratorUnavailable(plan, lastIndex, failure)
    }

    private fun <K : BackendKnob> asAcceleratorUnavailable(
        plan: BackendPlan<K>,
        index: Int,
        cause: Exception
    ): AiAndroidException.AcceleratorUnavailable {
        val remaining = plan.attempts.drop(index + 1).map { it.accelerator }
        return AiAndroidException.AcceleratorUnavailable(plan.attempts[index].accelerator, remaining)
            .also { it.addSuppressed(cause) }
    }
}
