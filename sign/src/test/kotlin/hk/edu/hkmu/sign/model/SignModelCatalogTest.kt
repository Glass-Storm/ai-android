package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SignModelCatalogTest {

    private val catalog = SignModelCatalog.Standard

    @Test
    fun `standard catalog ships both asl classifiers`() {
        assertEquals(2, catalog.all().size)
        assertEquals(
            listOf("tgcn-wlasl-asl100", "signbridge-asl-alphabet"),
            catalog.all().map { it.classifierId }
        )
    }

    @Test
    fun `tgcn isolated word model is recorded non commercial`() {
        val spec = catalog.forClassifierId("tgcn-wlasl-asl100")

        assertEquals(LicenseKind.NonCommercial, spec?.license?.kind)
        assertEquals("C-UDA-1.0", spec?.license?.spdxId)
        assertEquals(SignLanguages.Asl, spec?.language)
    }

    @Test
    fun `signbridge fingerspelling model is recorded permissive`() {
        val spec = catalog.forClassifierId("signbridge-asl-alphabet")

        assertEquals(LicenseKind.Permissive, spec?.license?.kind)
        assertEquals("MIT", spec?.license?.spdxId)
        assertEquals(28, spec?.classCount)
        assertEquals("A", spec?.labelFor(0))
        assertEquals("del", spec?.labelFor(26))
        assertEquals("space", spec?.labelFor(27))
    }

    @Test
    fun `language lookup finds an asl classifier and none for csl`() {
        assertNotNull(catalog.forLanguage(SignLanguages.Asl))
        assertNull(catalog.forLanguage(SignLanguages.Csl))
    }

    @Test
    fun `unknown classifier id raises typed model not available`() {
        val exception = assertThrows(AiAndroidException.ModelNotAvailable::class.java) {
            catalog.requireClassifier("csl")
        }

        assertEquals("csl", exception.modelId)
    }
}
