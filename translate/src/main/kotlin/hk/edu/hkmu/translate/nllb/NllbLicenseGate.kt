package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent

/**
 * License gate for the NLLB model.
 *
 * NLLB-200-distilled-600M is CC-BY-NC (non-commercial); see [NLLB_LICENSE]. The gate evaluates the
 * shared [LicensePolicy] for the host's [UsageIntent] before any model weight is touched, so a
 * `Commercial` host is refused with a typed [AiAndroidException.LicenseRestricted] instead of being
 * able to load the NC model. Under [UsageIntent.SelfUse] the model is permitted with the NC
 * acknowledgment recorded by `DefaultLicensePolicy`.
 */
internal class NllbLicenseGate(
    private val policy: LicensePolicy,
    private val intent: UsageIntent,
    private val license: ModelLicense = NLLB_LICENSE
) {
    /** @throws AiAndroidException.LicenseRestricted when [intent] is not permitted for the license. */
    fun ensureAllowed(modelId: String) {
        val decision = policy.evaluate(license, intent)
        if (decision is LicenseDecision.Restricted) {
            throw AiAndroidException.LicenseRestricted(modelId, license.kind, intent, decision.reason)
        }
    }

    companion object {
        /** NLLB-200-distilled-600M is licensed CC-BY-NC-4.0 (non-commercial). */
        val NLLB_LICENSE: ModelLicense = ModelLicense(
            kind = LicenseKind.NonCommercial,
            spdxId = "CC-BY-NC-4.0",
            notice = "NLLB-200 is licensed CC-BY-NC-4.0 (non-commercial); commercial use is prohibited."
        )
    }
}
