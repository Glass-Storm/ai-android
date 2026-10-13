package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Host-JVM coverage of [DiarizationLicenseGate]: the shipped permissive pair is allowed for both
 * intents, and a non-commercial override is refused for commercial use.
 */
class DiarizationLicenseGateTest {

    private val policy = DefaultLicensePolicy()
    private val id = "diarization-test"

    @Test
    fun `permissive pair is allowed for commercial use`() {
        DiarizationLicenseGate(policy, UsageIntent.Commercial).ensureAllowed(id)
    }

    @Test
    fun `permissive pair is allowed for self use`() {
        DiarizationLicenseGate(policy, UsageIntent.SelfUse).ensureAllowed(id)
    }

    @Test
    fun `non-commercial override is refused for commercial use`() {
        val gate = DiarizationLicenseGate(
            policy = policy,
            intent = UsageIntent.Commercial,
            license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
        )

        val exception = assertRestricted(gate)

        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(UsageIntent.Commercial, exception.intent)
        assertEquals(id, exception.modelId)
    }

    @Test
    fun `non-commercial override is allowed for self use`() {
        val gate = DiarizationLicenseGate(
            policy = policy,
            intent = UsageIntent.SelfUse,
            license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
        )

        gate.ensureAllowed(id)
    }

    private fun assertRestricted(gate: DiarizationLicenseGate): AiAndroidException.LicenseRestricted =
        assertThrows(AiAndroidException.LicenseRestricted::class.java) { gate.ensureAllowed(id) }
}
