@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.gesture

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tier A: the gesture `.task` is gated by the **shared** license path. A restricted entry must be
 * refused before the loader ever runs; a permissive one loads. The loader is a fake, so no
 * MediaPipe native is touched.
 */
class GestureLicenseGateTest {

    @Test
    fun `commercial intent restricts a non-commercial gesture entry before load`() {
        val loader = RecordingGestureLoader()
        val registry = GestureRegistryFactory.create(
            entry = nonCommercialEntry(),
            loader = loader,
            licensePolicy = DefaultLicensePolicy(),
            intent = UsageIntent.Commercial
        )

        val exception = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            registry.acquire(ENTRY_ID)
        }

        assertEquals(ENTRY_ID, exception.modelId)
        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(0, loader.loadCount)
    }

    @Test
    fun `self use permits the apache gesture recognizer and loads it once`() {
        val loader = RecordingGestureLoader()
        val registry = GestureRegistryFactory.create(
            entry = GestureRecognizerModelCatalog.GESTURE_ENTRY,
            loader = loader,
            licensePolicy = DefaultLicensePolicy(),
            intent = UsageIntent.SelfUse
        )

        registry.acquire(GestureRecognizerModelCatalog.GESTURE_MODEL_ID).use { lease ->
            assertNotNull(lease.handle)
        }
        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `commercial intent permits the apache gesture recognizer`() {
        val loader = RecordingGestureLoader()
        val registry = GestureRegistryFactory.create(
            entry = GestureRecognizerModelCatalog.GESTURE_ENTRY,
            loader = loader,
            licensePolicy = DefaultLicensePolicy(),
            intent = UsageIntent.Commercial
        )

        registry.acquire(GestureRecognizerModelCatalog.GESTURE_MODEL_ID).use { lease ->
            assertNotNull(lease.handle)
        }
        assertEquals(1, loader.loadCount)
    }

    private fun nonCommercialEntry(): GestureRecognizerManifestEntry = GestureRecognizerManifestEntry(
        modelId = ENTRY_ID,
        license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0"),
        fileName = "restricted_gesture.task"
    )

    private companion object {
        const val ENTRY_ID = "restricted-gesture"
    }
}
