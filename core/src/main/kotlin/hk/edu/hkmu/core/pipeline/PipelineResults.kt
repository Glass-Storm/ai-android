package hk.edu.hkmu.core.pipeline

/**
 * A millisecond time span produced by a source stage.
 *
 * @property startMillis Inclusive start, milliseconds from the start of the source stream.
 * @property endMillis Inclusive end, milliseconds from the start of the source stream.
 */
data class SegmentSpan(val startMillis: Long, val endMillis: Long) {
    companion object {
        /** A zero-length span for sources that carry no timing. */
        val Unknown: SegmentSpan = SegmentSpan(0L, 0L)
    }
}

/**
 * Result of one ASR segment flowing through the composed speech pipeline.
 *
 * Every field after [sourceText] is the output of the stage that produced it, so a caller can observe
 * exactly which configured stages ran and in what order.
 *
 * @property sourceText Raw ASR text (the source stage output).
 * @property text Final text after every configured text stage.
 * @property recombinedText Recombination output, or null when recombination did not run.
 * @property translatedText Translation output, or null when translation did not run.
 * @property span Timing carried from the source segment.
 */
data class SpeechResult(
    val sourceText: String,
    val text: String,
    val recombinedText: String?,
    val translatedText: String?,
    val span: SegmentSpan
)

/**
 * Result of one sign prediction flowing through the composed sign pipeline.
 *
 * @property label Raw sign label (the source stage output).
 * @property text Final text after every configured text stage.
 * @property recombinedText Recombination output, or null when recombination did not run.
 * @property confidence Source prediction confidence in `[0.0, 1.0]`.
 * @property span Timing carried from the source window.
 */
data class SignToTextResult(
    val label: String,
    val text: String,
    val recombinedText: String?,
    val confidence: Float,
    val span: SegmentSpan
)
