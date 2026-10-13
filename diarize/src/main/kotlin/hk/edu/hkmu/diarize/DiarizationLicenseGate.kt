package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent

/**
 * License gate for the local diarization models.
 *
 * The recommended pair — pyannote/segmentation-3.0 (MIT) and the 3D-Speaker CAM++ embedding
 * (Apache-2.0) — is permissive, so both [UsageIntent.SelfUse] and [UsageIntent.Commercial] are
 * allowed. The gate evaluates the shared [LicensePolicy] for the host's [UsageIntent] at load, before
 * any model weight is touched, so a host that pins a non-commercial pair for [UsageIntent.Commercial]
 * is refused with a typed [AiAndroidException.LicenseRestricted] instead of being able to load it.
 */
internal class DiarizationLicenseGate(
    private val policy: LicensePolicy,
    private val intent: UsageIntent,
    private val license: ModelLicense = DIARIZATION_LICENSE
) {
    /** @throws AiAndroidException.LicenseRestricted when [intent] is not permitted for the license. */
    fun ensureAllowed(modelId: String) {
        val decision = policy.evaluate(license, intent)
        if (decision is LicenseDecision.Restricted) {
            throw AiAndroidException.LicenseRestricted(modelId, license.kind, intent, decision.reason)
        }
    }

    companion object {
        /** License of the recommended pyannote + 3D-Speaker pair: permissive (MIT / Apache-2.0). */
        val DIARIZATION_LICENSE: ModelLicense = ModelLicense(
            kind = LicenseKind.Permissive,
            spdxId = "MIT",
            notice = "pyannote/segmentation-3.0 is MIT; the 3D-Speaker CAM++ embedding is Apache-2.0."
        )
    }
}
