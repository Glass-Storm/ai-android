package hk.edu.hkmu.testkit.license

import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent

/**
 * In-memory [LicensePolicy] for tests.
 *
 * By default it mirrors the decision surface documented on the real [LicensePolicy] contract:
 * `SelfUse` allows everything (recording an acknowledgment for anything non-permissive) while
 * `Commercial` allows only [LicenseKind.Permissive]. Supply [decisionOverride] to force a specific
 * outcome for a scenario, and inspect [evaluations] to assert what the policy was asked to decide.
 *
 * @property decisionOverride When set, bypasses the default policy for every evaluation.
 * @property acknowledgment Text returned as [LicenseDecision.Allowed.acknowledgment] for the
 *   permissive-adjacent default decisions.
 */
class FakeLicensePolicy(
    private val decisionOverride: ((ModelLicense, UsageIntent) -> LicenseDecision)? = null,
    private val acknowledgment: String = DEFAULT_ACKNOWLEDGMENT
) : LicensePolicy {

    /** Every `(license, intent)` pair passed to [evaluate], in order. */
    val evaluations: MutableList<Pair<ModelLicense, UsageIntent>> = mutableListOf()

    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision {
        evaluations += license to intent
        return decisionOverride?.invoke(license, intent) ?: defaultDecision(license, intent)
    }

    private fun defaultDecision(license: ModelLicense, intent: UsageIntent): LicenseDecision {
        return when (intent) {
            UsageIntent.SelfUse -> when (license.kind) {
                LicenseKind.Permissive -> LicenseDecision.Allowed()
                LicenseKind.NonCommercial,
                LicenseKind.UserProvided,
                LicenseKind.UnknownLicense -> LicenseDecision.Allowed(acknowledgment)
            }

            UsageIntent.Commercial -> when (license.kind) {
                LicenseKind.Permissive -> LicenseDecision.Allowed()
                LicenseKind.NonCommercial,
                LicenseKind.UserProvided,
                LicenseKind.UnknownLicense -> LicenseDecision.Restricted(
                    reason = "${license.kind} is not allowed for ${intent.name} use",
                    license = license,
                    intent = intent
                )
            }
        }
    }

    companion object {
        /** Default acknowledgment surfaced by the non-permissive `SelfUse` decisions. */
        const val DEFAULT_ACKNOWLEDGMENT: String = "SelfUse acknowledgment (testkit default)"
    }
}
