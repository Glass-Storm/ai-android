@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the production [DefaultLicensePolicy] decision surface.
 *
 * These pin the acceptance semantics using real [ModelLicense] values: `CC-BY-NC-4.0` = NLLB,
 * `C-UDA-1.0` = the WLASL-derived sign classifier, `Apache-2.0` = the ASL fingerspelling model, and
 * [LicenseKind.UnknownLicense] = a BYO model.
 */
class DefaultLicensePolicyTest {
    private val policy: LicensePolicy = DefaultLicensePolicy()

    @Test
    fun `commercial allows a permissive model`() {
        val decision = policy.evaluate(PERMISSIVE, UsageIntent.Commercial)

        assertTrue(decision is LicenseDecision.Allowed)
    }

    @Test
    fun `commercial blocks the cc-by-nc nllb model`() {
        val restricted = assertRestricted(NON_COMMERCIAL_NLLB, UsageIntent.Commercial)

        assertEquals(LicenseKind.NonCommercial, restricted.license.kind)
        assertEquals(UsageIntent.Commercial, restricted.intent)
        assertEquals(CC_BY_NC, restricted.license.spdxId)
    }

    @Test
    fun `commercial blocks the c-uda wlasl-derived sign model`() {
        val restricted = assertRestricted(NON_COMMERCIAL_SIGN, UsageIntent.Commercial)

        assertEquals(LicenseKind.NonCommercial, restricted.license.kind)
        assertEquals(C_UDA, restricted.license.spdxId)
    }

    @Test
    fun `commercial blocks a host-provided model`() {
        val restricted = assertRestricted(USER_PROVIDED, UsageIntent.Commercial)

        assertEquals(LicenseKind.UserProvided, restricted.license.kind)
    }

    @Test
    fun `commercial always blocks an unknown license`() {
        val restricted = assertRestricted(ModelLicense(LicenseKind.UnknownLicense), UsageIntent.Commercial)

        assertEquals(LicenseKind.UnknownLicense, restricted.license.kind)
    }

    @Test
    fun `self-use allows the cc-by-nc model with a recorded acknowledgment`() {
        val decision = policy.evaluate(NON_COMMERCIAL_NLLB, UsageIntent.SelfUse)

        val allowed = decision as LicenseDecision.Allowed
        assertEquals(CC_BY_NC_NOTICE, allowed.acknowledgment)
    }

    @Test
    fun `self-use synthesizes an acknowledgment when the nc notice is absent`() {
        val license = ModelLicense(LicenseKind.NonCommercial, spdxId = CC_BY_NC)
        val decision = policy.evaluate(license, UsageIntent.SelfUse)

        val acknowledgment = (decision as LicenseDecision.Allowed).acknowledgment
        assertFalse("an NC model must never be allowed silently", acknowledgment.isNullOrBlank())
    }

    @Test
    fun `self-use allows a permissive model`() {
        assertTrue(policy.evaluate(PERMISSIVE, UsageIntent.SelfUse) is LicenseDecision.Allowed)
    }

    @Test
    fun `self-use allows a host-provided model with an acknowledgment`() {
        val decision = policy.evaluate(USER_PROVIDED, UsageIntent.SelfUse)

        val acknowledgment = (decision as LicenseDecision.Allowed).acknowledgment
        assertFalse("a BYO model must never be allowed silently", acknowledgment.isNullOrBlank())
    }

    @Test
    fun `self-use allows an unknown license only with an acknowledgment`() {
        val decision = policy.evaluate(ModelLicense(LicenseKind.UnknownLicense), UsageIntent.SelfUse)

        val acknowledgment = (decision as LicenseDecision.Allowed).acknowledgment
        assertFalse("an unknown license must never be allowed silently", acknowledgment.isNullOrBlank())
    }

    private fun assertRestricted(license: ModelLicense, intent: UsageIntent): LicenseDecision.Restricted {
        val decision = policy.evaluate(license, intent)
        assertTrue("expected Restricted for ${license.kind} under $intent", decision is LicenseDecision.Restricted)
        return decision as LicenseDecision.Restricted
    }

    private companion object {
        const val CC_BY_NC = "CC-BY-NC-4.0"
        const val C_UDA = "C-UDA-1.0"
        const val CC_BY_NC_NOTICE = "Non-commercial use only; commercial use is prohibited."

        val PERMISSIVE: ModelLicense = ModelLicense(LicenseKind.Permissive, spdxId = "Apache-2.0")
        val NON_COMMERCIAL_NLLB: ModelLicense =
            ModelLicense(LicenseKind.NonCommercial, spdxId = CC_BY_NC, notice = CC_BY_NC_NOTICE)
        val NON_COMMERCIAL_SIGN: ModelLicense = ModelLicense(
            LicenseKind.NonCommercial,
            spdxId = C_UDA,
            notice = "Derived from the WLASL dataset; non-commercial research only."
        )
        val USER_PROVIDED: ModelLicense =
            ModelLicense(LicenseKind.UserProvided, spdxId = "Apache-2.0", notice = "host-declared")
    }
}
