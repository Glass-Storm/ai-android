package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.Language
import kotlinx.coroutines.flow.Flow

/**
 * Streaming translation contract.
 *
 * ### Emission semantics (text, not tokens)
 * [translate] returns **text**, not tokens: the flow emits one [TranslatedText] per input
 * sentence/segment, in order, then completes. It does not expose token-level partials. When script
 * conversion is configured, OpenCC (zh-Hant to zh-Hans) is applied before emission.
 *
 * ### Cancellation
 * Cancelling collection aborts the in-flight translation and stops further emissions.
 *
 * ### Backpressure
 * Implementations must use `callbackFlow`/`channelFlow` with `trySendBlocking` so a slow collector
 * throttles the engine rather than dropping a translation.
 */
fun interface TranslationEngine {
    /** Translates [text] and emits the result(s) in order. */
    fun translate(text: String): Flow<TranslatedText>
}

/**
 * A translated text chunk.
 *
 * @property text Translated text.
 * @property sourceLanguage Language of the input.
 * @property targetLanguage Language of [text].
 */
data class TranslatedText(
    val text: String,
    val sourceLanguage: Language,
    val targetLanguage: Language
)
