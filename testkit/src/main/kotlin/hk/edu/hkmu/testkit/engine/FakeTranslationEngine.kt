package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic in-memory [TranslationEngine] for tests.
 *
 * Emits [translations] in order, then completes. [failure] injects a typed error (e.g.
 * `InputTooLong` or `UnsupportedLanguage`).
 *
 * @property translations Results to emit, in order.
 * @property emissionDelayMillis Virtual delay applied before each emission (0 for immediate).
 * @property failure Optional typed error thrown as soon as collection starts.
 */
class FakeTranslationEngine(
    private val translations: List<TranslatedText> = emptyList(),
    private val emissionDelayMillis: Long = 0L,
    private val failure: AiAndroidException? = null
) : TranslationEngine {

    /** The input text of the most recent [translate] call. */
    var lastText: String? = null
        private set

    /** Results actually emitted, in emission order. */
    val emittedTranslations: MutableList<TranslatedText> = mutableListOf()

    /** True once the flow ran to completion. */
    var completed: Boolean = false
        private set

    /** True when collection was cancelled before completion. */
    var cancelled: Boolean = false
        private set

    /** True when [failure] was thrown. */
    var failed: Boolean = false
        private set

    override fun translate(text: String): Flow<TranslatedText> = flow {
        lastText = text
        try {
            failure?.let {
                failed = true
                throw it
            }
            for (translation in translations) {
                if (emissionDelayMillis > 0L) {
                    delay(emissionDelayMillis)
                }
                emit(translation)
                emittedTranslations += translation
            }
            completed = true
        } finally {
            if (!completed && !failed) {
                cancelled = true
            }
        }
    }
}
