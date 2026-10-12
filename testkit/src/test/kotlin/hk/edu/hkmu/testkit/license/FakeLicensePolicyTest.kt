@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.license

import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the default decision surface of the fake policy and its override seam. */
class FakeLicensePolicyTest {

    private val policy = FakeLicensePolicy()

    @Test
    fun `self use allows non-commercial with an acknowledgment`() {
        val decision = policy.evaluate(nc, UsageIntent.SelfUse)

        assertTrue(decision is LicenseDecision.Allowed)
        assertEquals(FakeLicensePolicy.DEFAULT_ACKNOWLEDGMENT, (decision as LicenseDecision.Allowed).acknowledgment)
    }

    @Test
    fun `commercial blocks non-commercial`() {
        val decision = policy.evaluate(nc, UsageIntent.Commercial)

        assertTrue(decision is LicenseDecision.Restricted)
    }

    @Test
    fun `commercial blocks unknown license`() {
        val decision = policy.evaluate(
            ModelLicense(LicenseKind.UnknownLicense),
            UsageIntent.Commercial
        )

        assertTrue(decision is LicenseDecision.Restricted)
    }

    @Test
    fun `commercial allows permissive`() {
        val decision = policy.evaluate(
            ModelLicense(LicenseKind.Permissive, spdxId = "Apache-2.0"),
            UsageIntent.Commercial
        )

        assertTrue(decision is LicenseDecision.Allowed)
    }

    @Test
    fun `override wins and every evaluation is recorded`() {
        val forced = LicenseDecision.Restricted("forced", nc, UsageIntent.SelfUse)
        val overriding = FakeLicensePolicy(decisionOverride = { _, _ -> forced })

        val decision = overriding.evaluate(nc, UsageIntent.SelfUse)

        assertEquals(forced, decision)
        assertEquals(listOf(nc to UsageIntent.SelfUse), overriding.evaluations)
    }

    private val nc = ModelLicense(LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
}
