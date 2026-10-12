package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [AsrEngine] for tests.
 *
 * Emits [segments] in order, optionally spaced by [emissionDelayMillis], then completes. When
 * [failure] is set the flow throws it after cancellation bookkeeping. Records what it emitted and
 * whether the flow completed, was cancelled, or failed, so tests can assert each terminal outcome
 * without touching a real model.
 *
 * @property segments Final segments to emit, in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakeAsrEngine(
    private val segments: List<TranscriptionSegment> = emptyList(),
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : AsrEngine {

    /** Segments actually emitted, in emission order. */
    val emittedSegments: MutableList<TranscriptionSegment> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment> = flow {
        try {
            failure?.let {
                failed = true
                throw it
            }
            for (segment in segments) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(segment)
                emittedSegments += segment
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }
}
