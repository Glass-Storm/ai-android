package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent

/**
 * Scenario 1: the real licence gate.
 *
 * Drives [DefaultLicensePolicy] directly — no fake policy. Shows that a `NonCommercial` model is
 * allowed (with a recorded acknowledgment) for `SelfUse` but restricted for `Commercial`, and that an
 * `UnknownLicense` model is allowed for `SelfUse` but always blocked for `Commercial`.
 */
object LicenseScenario {
    private val policy = DefaultLicensePolicy()

    private val nonCommercial = ModelLicense(
        kind = LicenseKind.NonCommercial,
        spdxId = "CC-BY-NC-4.0",
        notice = "Sign classifier derived from WLASL (C-UDA non-commercial terms)."
    )

    private val unknown = ModelLicense(kind = LicenseKind.UnknownLicense)

    /** Evaluates the four decisions and renders them. */
    fun run(): String = buildString {
        appendLine("Real policy: hk.edu.hkmu.core.license.DefaultLicensePolicy")
        appendLine("NonCommercial model (${nonCommercial.kind}, ${nonCommercial.spdxId}):")
        appendLine("  SelfUse    -> ${describe(policy.evaluate(nonCommercial, UsageIntent.SelfUse))}")
        appendLine("  Commercial -> ${describe(policy.evaluate(nonCommercial, UsageIntent.Commercial))}")
        appendLine("UnknownLicense model (${unknown.kind}):")
        appendLine("  SelfUse    -> ${describe(policy.evaluate(unknown, UsageIntent.SelfUse))}")
        append("  Commercial -> ${describe(policy.evaluate(unknown, UsageIntent.Commercial))}")
    }

    private fun describe(decision: LicenseDecision): String = when (decision) {
        is LicenseDecision.Allowed ->
            "Allowed" + (decision.acknowledgment?.let { " (acknowledgment: $it)" }.orEmpty())
        is LicenseDecision.Restricted -> "Restricted (reason: ${decision.reason})"
    }
}
