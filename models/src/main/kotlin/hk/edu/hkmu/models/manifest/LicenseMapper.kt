package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense

/**
 * Maps a manifest `license` token to the core [ModelLicense].
 *
 * Only tokens the library knows how to classify are accepted; an unknown token is refused by the
 * manifest validator rather than silently downgraded, so a vendor manifest can never smuggle an
 * unclassified license past the policy.
 */
object LicenseMapper {
    private val permissive = setOf(
        "APACHE-2.0",
        "MIT",
        "BSD-2-CLAUSE",
        "BSD-3-CLAUSE",
        "ISC",
        "MPL-2.0"
    )

    private val nonCommercial = setOf(
        "CC-BY-NC",
        "CC-BY-NC-3.0",
        "CC-BY-NC-4.0",
        "CC-BY-NC-SA-4.0",
        "CC-BY-NC-ND-4.0",
        "C-UDA-1.0"
    )

    private const val NC_NOTICE = "Non-commercial use only; commercial use is prohibited."

    fun toModelLicense(token: String): ModelLicense? {
        val normalized = token.trim()
        return when (normalized.uppercase()) {
            in permissive -> ModelLicense(LicenseKind.Permissive, spdxId = normalized)
            in nonCommercial -> ModelLicense(LicenseKind.NonCommercial, spdxId = normalized, notice = NC_NOTICE)
            else -> null
        }
    }
}
