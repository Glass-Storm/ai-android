package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.Language
import kotlinx.coroutines.flow.Flow

/**
 * Streaming speech-to-text contract.
 *
 * ### Emission semantics (final segments, not partials)
 * [transcribe] returns a flow of **final transcription segments only**: exactly one
 * [TranscriptionSegment] per detected utterance/segment, in chronological order. It does **not**
 * emit token-level or partial/interim hypotheses — the underlying SenseVoice recognizer is an
 * offline, whole-segment model, so no streaming partial exists to expose. Every emitted segment
 * therefore has `isFinal == true`. The flow completes normally after the last segment.
 *
 * ### Cancellation
 * Cancelling collection must release the native session and stop all further emissions; no segment
 * may be delivered after cancellation.
 *
 * ### Backpressure
 * Implementations bridge the native callback with `callbackFlow`/`channelFlow`. Where a native
 * thread can outrun the collector, use non-suspending `trySendBlocking` on the producer side so the
 * native callback applies backpressure instead of dropping a final segment.
 */
fun interface AsrEngine {
    /** Transcribes [audio] into ordered final segments. */
    fun transcribe(audio: Flow<AudioChunk>): Flow<TranscriptionSegment>
}

/**
 * A finalized ASR segment.
 *
 * @property text Recognized text for the segment.
 * @property startTimeMillis Segment start, milliseconds from the start of the audio stream.
 * @property endTimeMillis Segment end, milliseconds from the start of the audio stream.
 * @property isFinal Always true: this engine does not emit partials.
 * @property language Language of [text] when known.
 * @property speakerLabel Human-readable diarization label for the turn this segment falls in (for
 *   example `Speaker 1`), or `null` when no speaker attribution is available. Additive and optional:
 *   engines that do not diarize leave it `null`. This is the 1-based presentation form of
 *   [speakerId] (`"Speaker ${speakerId + 1}"`).
 * @property speakerId 0-based diarization id of that speaker (for example `0`), or `null` when no
 *   speaker attribution is available. [speakerLabel] is the 1-based presentation form; [speakerId]
 *   is the 0-based key with the same convention as [DiarizationSegment.speakerId]. The underlying
 *   diarizer assigns indices per run, so a host must not treat either value as stable across calls.
 */
data class TranscriptionSegment(
    val text: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isFinal: Boolean = true,
    val language: Language? = null,
    val speakerLabel: String? = null,
    val speakerId: Int? = null
)
