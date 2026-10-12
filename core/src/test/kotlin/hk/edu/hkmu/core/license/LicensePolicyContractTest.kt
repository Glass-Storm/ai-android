package hk.edu.hkmu.core.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference implementation of the documented decision surface. Later modules (registry, downloader,
 * policy layer) must honour the same contract; this class proves the interface is implementable and
 * pins the SelfUse / Commercial / UnknownLicense semantics.
 */
private class DocumentedLicensePolicy : LicensePolicy {
    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision = when (intent) {
        UsageIntent.SelfUse -> decideSelfUse(license)
        UsageIntent.Commercial -> decideCommercial(license)
    }

    private fun decideSelfUse(license: ModelLicense): LicenseDecision = when (license.kind) {
        LicenseKind.Permissive -> LicenseDecision.Allowed(license.notice)
        LicenseKind.NonCommercial -> LicenseDecision.Allowed(license.notice)
        LicenseKind.UserProvided -> LicenseDecision.Allowed(license.notice)
        LicenseKind.UnknownLicense -> LicenseDecision.Allowed(license.notice)
    }

    private fun decideCommercial(license: ModelLicense): LicenseDecision = when (license.kind) {
        LicenseKind.Permissive -> LicenseDecision.Allowed(license.notice)
        LicenseKind.NonCommercial -> restricted(license, UsageIntent.Commercial)
        LicenseKind.UserProvided -> restricted(license, UsageIntent.Commercial)
        LicenseKind.UnknownLicense -> restricted(license, UsageIntent.Commercial)
    }

    private fun restricted(license: ModelLicense, intent: UsageIntent): LicenseDecision.Restricted =
        LicenseDecision.Restricted(
            reason = "license ${license.kind.name} is not permitted for ${intent.name}",
            license = license,
            intent = intent
        )
}

/** Locks the [LicensePolicy] contract as an interface with the documented decision surface. */
class LicensePolicyContractTest {

    private val policy: LicensePolicy = DocumentedLicensePolicy()

    @Test
    fun `license policy is an interface`() {
        assertTrue(LicensePolicy::class.java.isInterface)
    }

    @Test
    fun `commercial blocks non-commercial`() {
        val decision = policy.evaluate(
            ModelLicense(LicenseKind.NonCommercial, spdxId = CC_BY_NC, notice = NOTICE),
            UsageIntent.Commercial
        )
        assertTrue(decision is LicenseDecision.Restricted)
    }

    @Test
    fun `self-use permits non-commercial with acknowledgment`() {
        val decision = policy.evaluate(
            ModelLicense(LicenseKind.NonCommercial, spdxId = CC_BY_NC, notice = NOTICE),
            UsageIntent.SelfUse
        )
        assertEquals(LicenseDecision.Allowed(NOTICE), decision)
    }

    @Test
    fun `unknown license is always blocked for commercial`() {
        val decision = policy.evaluate(
            ModelLicense(LicenseKind.UnknownLicense),
            UsageIntent.Commercial
        )
        assertTrue(decision is LicenseDecision.Restricted)
    }

    @Test
    fun `permissive license is allowed for both intents`() {
        val permissive = ModelLicense(LicenseKind.Permissive, spdxId = APACHE_2)
        assertTrue(policy.evaluate(permissive, UsageIntent.SelfUse) is LicenseDecision.Allowed)
        assertTrue(policy.evaluate(permissive, UsageIntent.Commercial) is LicenseDecision.Allowed)
    }

    private companion object {
        const val CC_BY_NC = "CC-BY-NC-4.0"
        const val APACHE_2 = "Apache-2.0"
        const val NOTICE = "Non-commercial research use only."
    }
}
