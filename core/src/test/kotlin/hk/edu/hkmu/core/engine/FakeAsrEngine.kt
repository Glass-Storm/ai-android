package hk.edu.hkmu.core.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [AsrEngine] used by tests (and reusable by the future `testkit` module).
 *
 * Emits [segments] in order, optionally spaced by [emissionDelayMillis], then completes. Records
 * what it emitted and whether it completed or was cancelled, so cancellation can be asserted.
 *
 * @property segments Segments to emit in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 */
class FakeAsrEngine(
    private val segments: List<TranscriptionSegment>,
    private val emissionDelayMillis: Long = 0L
) : AsrEngine {

    /** Segments actually emitted, in emission order. */
    val emittedSegments: MutableList<TranscriptionSegment> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    override fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment> = flow {
        try {
            for (segment in segments) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(segment)
                emittedSegments += segment
            }
            completed = true
        } finally {
            if (!completed) {
                cancelled = true
            }
        }
    }
}
