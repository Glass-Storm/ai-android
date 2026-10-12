package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [PostProcessor] for tests.
 *
 * Emits [tokens] in order when explicitly supplied; otherwise splits the input on whitespace so a
 * caller always observes a realistic token stream. Unlike the ASR fake these emissions are partial,
 * matching the documented token-stream contract. [failure] injects a typed error (e.g.
 * `InputTooLong`).
 *
 * @property tokens Explicit token sequence; when null the input is split on whitespace.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakePostProcessor(
    private val tokens: List<String>? = null,
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : PostProcessor {

    /** The input text of the most recent [process] call. */
    var lastInput: String? = null
        private set

    /** Tokens actually emitted, in emission order. */
    val emittedTokens: MutableList<String> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun process(text: String): Flow<String> = flow {
        lastInput = text
        try {
            failure?.let {
                failed = true
                throw it
            }
            for (token in tokens ?: text.split(' ')) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(token)
                emittedTokens += token
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }
}
