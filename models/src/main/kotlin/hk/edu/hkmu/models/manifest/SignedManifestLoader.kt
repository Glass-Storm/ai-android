package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.serialization.SerializationException

/**
 * Loads a vendor-signed manifest into typed [ModelDescriptor]s.
 *
 * The order is deliberate: decode the envelope, verify the detached signature, and ONLY THEN decode
 * and validate the payload into descriptors. A tampered or unsigned manifest therefore fails with
 * [AiAndroidException.ManifestSignatureInvalid] without any descriptor ever being constructed.
 */
class SignedManifestLoader(
    private val signatureVerifier: ManifestSignatureVerifier = ManifestSignatureVerifier()
) {
    /**
     * @throws AiAndroidException.ManifestSignatureInvalid before any descriptor is produced.
     * @throws AiAndroidException.ManifestInvalid when a signature-valid payload is malformed.
     */
    fun load(envelopeJson: String): SignedManifest {
        val envelope = decodeEnvelope(envelopeJson)
        signatureVerifier.verify(envelope.manifestId, envelope.payload, envelope.signature)
        val body = decodeBody(envelope)
        val source = ModelSource.Vendor(envelope.manifestId, body.release)
        val descriptors = body.models.map { toDescriptor(it, source) }
        return SignedManifest(envelope.manifestId, body.release, descriptors)
    }

    @Suppress("SwallowedException")
    private fun decodeEnvelope(json: String): SignedManifestEnvelope = try {
        ManifestCodec.json.decodeFromString(SignedManifestEnvelope.serializer(), json)
    } catch (error: SerializationException) {
        throw AiAndroidException.ManifestInvalid(UNKNOWN_MANIFEST, "malformed envelope: ${error.message}")
    }

    @Suppress("SwallowedException")
    private fun decodeBody(envelope: SignedManifestEnvelope): ManifestBody = try {
        ManifestCodec.json.decodeFromString(ManifestBody.serializer(), envelope.payload)
    } catch (error: SerializationException) {
        throw AiAndroidException.ManifestInvalid(envelope.manifestId, "signed payload malformed: ${error.message}")
    }

    private fun toDescriptor(entry: ManifestEntry, source: ModelSource): ModelDescriptor {
        ManifestValidation.requireId(entry.id)
        val licenseToken = entry.license?.takeIf { it.isNotBlank() }
            ?: ManifestValidation.invalid(entry.id, "missing license")
        val license = LicenseMapper.toModelLicense(licenseToken)
            ?: ManifestValidation.invalid(entry.id, "unrecognized license '$licenseToken'")
        val format = ModelFormat.fromId(entry.format)
            ?: ManifestValidation.invalid(entry.id, "unknown format '${entry.format}'")
        val version = try {
            ModelVersion.parse(entry.version)
        } catch (error: IllegalArgumentException) {
            ManifestValidation.invalid(entry.id, error.message ?: "invalid version")
        }
        ManifestValidation.requireSha256(entry.id, entry.sha256)
        ManifestValidation.requireSize(entry.id, entry.sizeBytes)
        ManifestValidation.requireUrls(entry.id, entry.urls)
        val abis = entry.abi.mapTo(linkedSetOf()) { token ->
            Abi.fromDir(token) ?: ManifestValidation.invalid(entry.id, "unknown abi '$token'")
        }
        ManifestValidation.requireAbis(entry.id, abis)
        return ModelDescriptor(
            id = entry.id,
            license = license,
            version = version,
            sha256 = entry.sha256.lowercase(),
            abi = abis,
            sizeBytes = entry.sizeBytes,
            urls = entry.urls.toList(),
            format = format,
            source = source
        )
    }

    private companion object {
        const val UNKNOWN_MANIFEST = "<unknown>"
    }
}

/** A fully authenticated manifest: every descriptor here came from a signature-verified payload. */
data class SignedManifest(
    val manifestId: String,
    val release: String,
    val descriptors: List<ModelDescriptor>
) {
    fun descriptor(modelId: String): ModelDescriptor? = descriptors.firstOrNull { it.id == modelId }
}
