package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TtsEngine
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Peak sample value used by the default synthetic PCM chunk (kept out of detekt's magic-number path). */
private const val FAKE_PCM_PEAK = 0.25f

/**
 * Deterministic in-memory [TtsEngine] for tests.
 *
 * Emits [chunks] in order, then completes. When [chunks] is left at its default a single non-empty
 * chunk of normalized PCM is produced, so a caller always observes real audio. [failure] injects a
 * typed error (e.g. `UnsupportedLanguage`).
 *
 * @property chunks PCM chunks to emit, in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakeTtsEngine(
    private val chunks: List<AudioChunk> = listOf(DEFAULT_CHUNK),
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : TtsEngine {

    /** The input text of the most recent [synthesize] call. */
    var lastText: String? = null
        private set

    /** Chunks actually emitted, in emission order. */
    val emittedChunks: MutableList<AudioChunk> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun synthesize(text: String): Flow<AudioChunk> = flow {
        lastText = text
        try {
            failure?.let {
                failed = true
                throw it
            }
            for (chunk in chunks) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(chunk)
                emittedChunks += chunk
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }

    companion object {
        /** Default non-empty PCM chunk (`0.0, +0.25, -0.25`). */
        val DEFAULT_CHUNK: AudioChunk = AudioChunk(floatArrayOf(0f, FAKE_PCM_PEAK, -FAKE_PCM_PEAK))
    }
}
