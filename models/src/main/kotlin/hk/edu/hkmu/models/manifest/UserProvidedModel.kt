package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense

/**
 * A host-supplied (bring-your-own) model that is NOT part of the vendor-signed manifest, e.g. a BYO
 * CSL sign classifier.
 *
 * [declaredLicense] is the host's claim (an SPDX id or free text) and is recorded for attribution,
 * but it never upgrades the trust level: registration always yields
 * [hk.edu.hkmu.core.license.LicenseKind.UnknownLicense].
 */
data class UserProvidedModel(
    val id: String,
    val declaredLicense: String?,
    val version: String,
    val sha256: String,
    val abi: Set<Abi>,
    val sizeBytes: Long,
    val urls: List<String>,
    val format: ModelFormat
)

/**
 * Registers [UserProvidedModel]s into verified [ModelDescriptor]s.
 *
 * There is no signature here by design: a BYO model is trusted only as far as the host declares, so
 * its license is pinned to `UnknownLicense` regardless of [UserProvidedModel.declaredLicense]. A
 * host therefore cannot use this path to relabel a model as permissive, and cannot sign the vendor
 * manifest (that requires a pinned vendor key, see [SignedManifestLoader]).
 */
class UserProvidedModelRegistrar {
    /**
     * @throws AiAndroidException.ManifestInvalid when a field is malformed (id, sha256, size, urls,
     *   abi, or version).
     */
    fun register(model: UserProvidedModel): ModelDescriptor {
        ManifestValidation.requireId(model.id)
        ManifestValidation.requireSha256(model.id, model.sha256)
        ManifestValidation.requireSize(model.id, model.sizeBytes)
        ManifestValidation.requireUrls(model.id, model.urls)
        ManifestValidation.requireAbis(model.id, model.abi)
        val version = try {
            ModelVersion.parse(model.version)
        } catch (error: IllegalArgumentException) {
            ManifestValidation.invalid(model.id, error.message ?: "invalid version")
        }
        return ModelDescriptor(
            id = model.id,
            license = ModelLicense(LicenseKind.UnknownLicense, spdxId = model.declaredLicense, notice = BYO_NOTICE),
            version = version,
            sha256 = model.sha256.lowercase(),
            abi = model.abi.toSet(),
            sizeBytes = model.sizeBytes,
            urls = model.urls.toList(),
            format = model.format,
            source = ModelSource.UserProvided
        )
    }

    private companion object {
        const val BYO_NOTICE = "Host-provided model with an unverified license; treated as UnknownLicense."
    }
}
