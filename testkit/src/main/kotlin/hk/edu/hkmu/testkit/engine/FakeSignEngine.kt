package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [SignEngine] for tests.
 *
 * Emits [predictions] in order (modelling one emission per window boundary), then completes.
 * [failure] injects a typed error (e.g. `MalformedFrame` or `UnsupportedLanguage`).
 *
 * @property predictions Predictions to emit, in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakeSignEngine(
    private val predictions: List<SignPrediction> = emptyList(),
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : SignEngine {

    /** Frames observed at the most recent [recognize] call. */
    var consumedFrames: Int = 0
        private set

    /** Predictions actually emitted, in emission order. */
    val emittedPredictions: MutableList<SignPrediction> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun recognize(frames: Flow<SignFrame>): Flow<SignPrediction> = flow {
        try {
            failure?.let {
                failed = true
                throw it
            }
            for (prediction in predictions) {
                frames.collect { consumedFrames += 1 }
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(prediction)
                emittedPredictions += prediction
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }
}
