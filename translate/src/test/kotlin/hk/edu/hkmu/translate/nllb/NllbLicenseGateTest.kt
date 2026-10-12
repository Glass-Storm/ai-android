@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.UsageIntent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tier A acceptance for the CC-BY-NC license gate: NLLB through the **engine** under `Commercial`
 * must be refused with a typed `LicenseRestricted`, while `SelfUse` is permitted.
 */
class NllbLicenseGateTest {

    private val absentModelDir = File("/tmp/nllb-model-absent")

    @Test
    fun `commercial policy restricts nllb at engine creation`() {
        val exception = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            NllbTranslationEngine.create(
                TranslationConfig(),
                absentModelDir,
                DefaultLicensePolicy(),
                UsageIntent.Commercial
            )
        }

        assertEquals(NllbTranslationEngine.MODEL_ID, exception.modelId)
        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(UsageIntent.Commercial, exception.intent)
    }

    @Test
    fun `self use permits nllb`() {
        val engine = NllbTranslationEngine.create(
            TranslationConfig(),
            absentModelDir,
            DefaultLicensePolicy(),
            UsageIntent.SelfUse
        )

        engine.close()
    }

    @Test
    fun `gate surfaces the restricted license kind and intent`() {
        val gate = NllbLicenseGate(DefaultLicensePolicy(), UsageIntent.Commercial)

        val exception = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            gate.ensureAllowed("test-model")
        }

        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(UsageIntent.Commercial, exception.intent)
    }

    @Test
    fun `gate permits self use of the nc model`() {
        NllbLicenseGate(DefaultLicensePolicy(), UsageIntent.SelfUse).ensureAllowed("test-model")
    }
}
