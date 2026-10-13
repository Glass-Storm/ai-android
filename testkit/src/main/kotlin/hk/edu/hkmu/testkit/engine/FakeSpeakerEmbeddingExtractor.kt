package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.SpeakerEmbeddingExtractor
import hk.edu.hkmu.core.engine.Voiceprint
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [SpeakerEmbeddingExtractor] for tests.
 *
 * Consumes the incoming [AudioChunk]s, then emits [voiceprints] in order (optionally spaced by
 * [emissionDelayMillis]) and completes. When [failure] is set the flow throws it as soon as
 * collection starts. Records the total sample count it consumed and whether the flow completed, was
 * cancelled, or failed, so tests can assert each terminal outcome without touching a real model.
 *
 * @property voiceprints Voiceprints to emit, in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakeSpeakerEmbeddingExtractor(
    private val voiceprints: List<Voiceprint> = emptyList(),
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : SpeakerEmbeddingExtractor {

    /** Voiceprints actually emitted, in emission order. */
    val emittedVoiceprints: MutableList<Voiceprint> = mutableListOf()

    /** Total number of PCM samples consumed from the input flow. */
    var consumedSampleCount: Int = 0
        private set

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun extract(audio: Flow<AudioChunk>): Flow<Voiceprint> = flow {
        try {
            audio.collect { chunk -> consumedSampleCount += chunk.samples.size }
            failure?.let {
                failed = true
                throw it
            }
            for (voiceprint in voiceprints) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(voiceprint)
                emittedVoiceprints += voiceprint
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }
}
