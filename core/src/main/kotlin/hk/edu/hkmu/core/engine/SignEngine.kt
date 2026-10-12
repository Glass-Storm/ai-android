package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * Windowed sign-language recognition contract.
 *
 * ### Emission semantics (window boundary)
 * [recognize] consumes a stream of landmark [SignFrame]s and emits a [SignPrediction] **only at a
 * window boundary**: one prediction is produced once a full window of `windowSize` frames
 * (advanced by `stride`) has been accumulated. It never emits per frame. When `stride < windowSize`
 * consecutive windows overlap, so predictions may describe overlapping time ranges. The flow
 * completes after the final window, or when the input flow completes.
 *
 * ### Cancellation
 * Cancelling collection discards the in-progress window and stops all further predictions.
 *
 * ### Backpressure
 * Use `callbackFlow`/`channelFlow` + `trySendBlocking`; the classifier runs on a worker thread, so a
 * slow collector must throttle window emission rather than skip a window.
 */
fun interface SignEngine {
    /** Recognizes signs from [frames], emitting at each window boundary. */
    fun recognize(frames: Flow<SignFrame>): Flow<SignPrediction>
}

/**
 * One normalized landmark frame supplied by the caller's extractor.
 *
 * @property landmarks Flattened normalized landmark vector.
 * @property timestampMillis Presentation timestamp in milliseconds.
 */
data class SignFrame(
    val landmarks: FloatArray,
    val timestampMillis: Long
)

/**
 * A sign prediction emitted at a window boundary.
 *
 * @property label Predicted gloss/label.
 * @property confidence Model confidence in `[0.0, 1.0]`.
 * @property windowStartMillis Start timestamp of the classified window.
 * @property windowEndMillis End timestamp of the classified window.
 */
data class SignPrediction(
    val label: String,
    val confidence: Float,
    val windowStartMillis: Long,
    val windowEndMillis: Long
)
