package hk.edu.hkmu.models.store

import kotlinx.serialization.Serializable

/**
 * Sidecar written next to a `<artifact>.part` file so a resumed (or restarted) download can prove
 * the partial belongs to the artifact it is about to complete.
 *
 * Persisted progress is therefore durable across process restarts: the partial file's length is the
 * resume offset, and this record pins which descriptor/mirror produced it.
 */
@Serializable
internal data class PartialState(
    val modelId: String,
    val sha256: String,
    val sizeBytes: Long,
    val sourceUrl: String
)
