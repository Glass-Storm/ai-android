package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * Recombination / post-processing contract (small LLM).
 *
 * ### Emission semantics (token stream)
 * Unlike the other engines, [process] is a **token stream**: it emits partial text pieces as the
 * LLM generates them, then completes. Concatenating all emissions yields the final text; unlike
 * ASR, these emissions are not final-only and consumers may render them incrementally. When
 * recombination is disabled in configuration, implementations may bypass the LLM and emit the
 * input unchanged as a single token, preserving the streaming shape.
 *
 * ### Cancellation
 * Cancelling collection stops generation and unloads the session if the engine owns it.
 *
 * ### Backpressure
 * The JNI token callback must push through `callbackFlow` + `trySendBlocking` so a slow collector
 * throttles generation rather than dropping tokens.
 */
fun interface PostProcessor {
    /** Streams post-processed tokens for [text]. */
    fun process(text: String): Flow<String>
}
