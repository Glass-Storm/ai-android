package hk.edu.hkmu.sign.pipeline

/**
 * One sign-recognition result emitted by [SignPipeline] at a classified window boundary.
 *
 * ### Output ceiling: letters / isolated signs — NOT sentence translation
 * The pipeline is a sign **recognition** path, not a continuous sign-language translator. [label]
 * is a single classifier gloss: one fingerspelled letter (e.g. `A`), a delimiter (`del`, `space`),
 * or one isolated sign word (e.g. a WLASL gloss). [text] is that gloss, optionally recombined by the
 * small-LLM post-processor into cleaner spacing/wording. The pipeline **never** produces a
 * translated sentence and makes no claim to: it emits an ordered sequence of isolated results whose
 * consumer may assemble them, but the library itself does not translate continuous signing into
 * sentences.
 *
 * @property label Raw classifier gloss for the window (`spec.labelFor(argMax)`).
 * @property text [label], or the post-processor's recombination of it when [recombined] is true.
 * @property confidence Softmax confidence of [label] in `[0.0, 1.0]`.
 * @property windowIndex 0-based window ordinal within the stream (emission order).
 * @property windowStartMillis Timestamp of the window's first frame.
 * @property windowEndMillis Timestamp of the window's last frame.
 * @property lowConfidence True when [confidence] is below the configured threshold; such a result is
 *   still emitted (and flagged) but is **never** recombined.
 * @property recombined True iff the small-LLM post-processor actually ran for this result.
 */
@Suppress("LongParameterList")
data class SignResult(
    val label: String,
    val text: String,
    val confidence: Float,
    val windowIndex: Int,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val lowConfidence: Boolean,
    val recombined: Boolean
)
