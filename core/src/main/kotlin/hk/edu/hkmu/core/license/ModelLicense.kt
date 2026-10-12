package hk.edu.hkmu.core.license

/**
 * Per-model license descriptor.
 *
 * @property kind Coarse classification driving the policy decision.
 * @property spdxId Optional SPDX identifier (e.g. `Apache-2.0`) when known.
 * @property notice Human-readable attribution/acknowledgment text to surface when the model is used.
 */
data class ModelLicense(
    val kind: LicenseKind,
    val spdxId: String? = null,
    val notice: String? = null
)
