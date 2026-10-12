package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserProvidedModelTest {
    private val registrar = UserProvidedModelRegistrar()

    @Suppress("LongParameterList")
    private fun byo(
        id: String = "csl-byo",
        declaredLicense: String? = "Apache-2.0",
        sha256: String = SHA,
        abi: Set<Abi> = setOf(Abi.Arm64V8a, Abi.X86_64),
        sizeBytes: Long = MODEL_SIZE,
        urls: List<String> = listOf(DEFAULT_URL)
    ): UserProvidedModel = UserProvidedModel(
        id,
        declaredLicense,
        "1.0.0",
        sha256,
        abi,
        sizeBytes,
        urls,
        ModelFormat.Tflite
    )

    @Test
    fun `byo registration is always UnknownLicense even with a permissive declaration`() {
        val descriptor = registrar.register(byo(declaredLicense = "Apache-2.0"))
        assertEquals(LicenseKind.UnknownLicense, descriptor.license.kind)
        assertEquals("Apache-2.0", descriptor.license.spdxId)
        assertEquals(ModelSource.UserProvided, descriptor.source)
        assertEquals(ModelFormat.Tflite, descriptor.format)
    }

    @Test
    fun `byo without a declared license is still UnknownLicense`() {
        assertEquals(LicenseKind.UnknownLicense, registrar.register(byo(declaredLicense = null)).license.kind)
    }

    @Test
    fun `byo malformed fields are rejected as ManifestInvalid`() {
        val cases = listOf(
            byo(id = " ") to "id",
            byo(sha256 = "nothex") to "sha256",
            byo(sizeBytes = 0L) to "size",
            byo(urls = emptyList()) to "url",
            byo(abi = emptySet()) to "abi"
        )
        cases.forEach { (model, fragment) ->
            val error = assertThrows(AiAndroidException.ManifestInvalid::class.java) { registrar.register(model) }
            assertEquals(model.id, error.manifestId)
            assertEquals(true, error.reason.contains(fragment))
        }
    }

    private companion object {
        const val DEFAULT_URL = "https://cdn.example.invalid/models/csl"
        const val MODEL_SIZE = 2048L
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
