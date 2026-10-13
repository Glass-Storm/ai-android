package hk.edu.hkmu.core.engine

import kotlinx.coroutines.flow.Flow

/**
 * Local speaker-embedding contract: **audio → voiceprint**.
 *
 * This is the enrollment half of the diarization story. Where [DiarizationEngine] answers *who spoke
 * when* but labels speakers only with a per-run index, an embedding extractor turns one speaker's
 * audio into a reusable **voiceprint** that can be compared to a previously enrolled voiceprint and
 * mapped to a stable display name (see [EnrollmentDirectory]). Everything runs locally; there is no
 * cloud call.
 *
 * ### Emission semantics (one voiceprint per utterance)
 * [extract] returns a cold flow that emits **exactly one** [Voiceprint] for the supplied audio, then
 * completes. Empty audio emits nothing and completes. The embedding is a whole-utterance model, so
 * there is no partial/per-chunk vector to expose — feed it one speaker's isolated turn (for example a
 * [DiarizationSegment] span) rather than an entire multi-speaker recording.
 *
 * ### Cancellation
 * Cancelling collection must release the native session and stop all further emissions; no
 * [Voiceprint] may be delivered after cancellation.
 *
 * ### Backpressure
 * The backend is an offline, whole-utterance model, so the flow buffers the incoming [AudioChunk]s
 * before it runs and must not block the collector.
 */
fun interface SpeakerEmbeddingExtractor {
    /** Extracts the speaker voiceprint for [audio]. */
    fun extract(audio: Flow<AudioChunk>): Flow<Voiceprint>
}

/**
 * A speaker voiceprint: the embedding vector produced by [SpeakerEmbeddingExtractor].
 *
 * The vector is model-specific and **not** normalized or comparable across different embedding
 * models; compare two voiceprints only when they were produced by the same model and have the same
 * dimension. Matching is a pure operation on [embedding] (see [VoiceprintMatcher]), so a voiceprint
 * can be persisted and re-loaded by a host without carrying any live native handle.
 *
 * @property embedding Raw embedding vector. Its length ([FloatArray.size]) is the model's embedding
 *   dimension.
 * @property modelId Identifier of the embedding model that produced [embedding], or `null` when the
 *   producer does not expose one. Carried for provenance only; it does not affect matching, but two
 *   voiceprints from different models are not comparable.
 */
data class Voiceprint(
    val embedding: FloatArray,
    val modelId: String? = null
)
