package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.license.ModelLicense

/** Where a verified [ModelDescriptor] came from. */
sealed interface ModelSource {
    /** A model enumerated by a signature-verified vendor manifest. */
    data class Vendor(val manifestId: String, val release: String) : ModelSource

    /** A host-supplied (bring-your-own) model registered outside the vendor manifest. */
    data object UserProvided : ModelSource
}

/**
 * A model that has passed its trust gate: either a signature-verified vendor manifest entry or a
 * registered [UserProvidedModel].
 *
 * The constructor is internal so a descriptor can only be produced by [SignedManifestLoader] (after
 * signature verification) or [UserProvidedModelRegistrar]; consumers read descriptors, they never
 * mint one. This is what makes "no descriptor exists for an unverified manifest" enforceable.
 */
@ConsistentCopyVisibility
data class ModelDescriptor internal constructor(
    val id: String,
    val license: ModelLicense,
    val version: ModelVersion,
    val sha256: String,
    val abi: Set<Abi>,
    val sizeBytes: Long,
    val urls: List<String>,
    val format: ModelFormat,
    val source: ModelSource
) {
    /** True when this artifact runs on at least one of the device [availableAbis]. */
    fun supportsAbis(availableAbis: Set<Abi>): Boolean = abi.contains(Abi.Any) || abi.any { it in availableAbis }
}
