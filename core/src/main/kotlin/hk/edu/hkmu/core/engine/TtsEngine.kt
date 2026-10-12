package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * Streaming text-to-speech contract.
 *
 * ### Emission semantics (PCM final chunks)
 * [synthesize] returns the synthesized audio as an ordered stream of [AudioChunk]s of normalized
 * Float32 PCM in `[-1.0, 1.0]` at the configured sample rate. Chunks are contiguous and their
 * concatenation is the complete utterance; the flow completes after the final chunk. This is a
 * final-chunk contract — no partial re-synthesis or speculative emissions.
 *
 * ### Cancellation
 * Cancelling collection stops synthesis and frees the native session.
 *
 * ### Backpressure
 * Producers must use `callbackFlow` + `trySendBlocking` so a slow collector pauses native synthesis
 * instead of dropping or reordering PCM chunks.
 */
fun interface TtsEngine {
    /** Synthesizes [text] into ordered PCM chunks. */
    fun synthesize(text: String): Flow<AudioChunk>
}
