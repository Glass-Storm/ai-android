package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * Local speaker-diarization contract.
 *
 * Diarization answers **who spoke when**: it cuts `audio` into speaker turns and labels each turn,
 * but it does not recognize any text. A host typically runs it over the same audio it feeds to an
 * [AsrEngine] and folds the resulting [DiarizationSegment]s onto the matching [TranscriptionSegment]
 * spans (see `TranscriptionSegment.speakerLabel`/`TranscriptionSegment.speakerId`).
 *
 * ### Emission semantics
 * [diarize] returns a cold flow of **final, ordered** [DiarizationSegment]s: exactly one segment per
 * detected speaker turn, in chronological order, with non-overlapping
 * `[startTimeMillis, endTimeMillis)` spans. Within one run every turn of the same speaker carries the
 * same [DiarizationSegment.speakerLabel] and [DiarizationSegment.speakerId]. The flow completes
 * normally after the last turn; empty audio emits nothing and completes.
 *
 * Speaker indices are assigned by the diarizer **per run**, so neither the label nor the id is stable
 * across separate [diarize] calls and a host must not use them to correlate speakers between runs.
 *
 * ### Cancellation
 * Cancelling collection must release the native session and stop all further emissions; no segment
 * may be delivered after cancellation.
 *
 * ### Backpressure
 * The backend is an offline, whole-utterance model, so the flow buffers the incoming [AudioChunk]s
 * before it runs. Implementations surface progress through the same cold-flow contract and must not
 * block the collector.
 */
fun interface DiarizationEngine {
    /** Diarizes `audio` into ordered speaker turns. */
    fun diarize(audio: Flow<AudioChunk>): Flow<DiarizationSegment>
}

/**
 * One finalized speaker turn.
 *
 * @property startTimeMillis Turn start, milliseconds from the start of the audio stream.
 * @property endTimeMillis Turn end, milliseconds from the start of the audio stream.
 * @property speakerLabel Human-readable label for the speaker of this turn (for example `Speaker 1`),
 *   the 1-based presentation form of [speakerId] (`"Speaker ${speakerId + 1}"`). Within one run every
 *   turn of the same speaker shares this label.
 * @property speakerId 0-based id of the speaker of this turn, as assigned by the diarizer (for
 *   example `0`), or `null` when the producer does not expose an id. Uses the same convention as
 *   [TranscriptionSegment.speakerId]: [speakerLabel] is the 1-based display form and [speakerId] the
 *   0-based key. Assigned per run, so it is **not** stable across calls.
 */
data class DiarizationSegment(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val speakerLabel: String,
    val speakerId: Int? = null
)
