package hk.edu.hkmu.models.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Detached-signature envelope: [signature] authenticates the exact UTF-8 bytes of [payload].
 *
 * The signature is verified BEFORE [payload] is decoded into descriptors, so a tampered or unsigned
 * payload can never yield a [ModelDescriptor].
 */
@Serializable
data class SignedManifestEnvelope(
    val manifestId: String,
    val keyId: String? = null,
    val payload: String,
    val signature: String? = null
)

/** Signed body: a release tag plus the model entries it enumerates. */
@Serializable
data class ManifestBody(
    val release: String,
    val models: List<ManifestEntry>
)

/** Raw per-model entry exactly as it appears in the (not yet trusted) manifest payload. */
@Serializable
data class ManifestEntry(
    val id: String,
    val license: String? = null,
    val version: String,
    val sha256: String,
    val abi: List<String> = emptyList(),
    val sizeBytes: Long,
    val urls: List<String>,
    val format: String
)

/** Canonical JSON for the manifest schema; strict so unexpected fields fail loudly. */
object ManifestCodec {
    val json: Json = Json {
        ignoreUnknownKeys = false
    }
}
