package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSignatureTest {
    private val signer = TestManifestSigner()
    private val loader = SignedManifestLoader(ManifestSignatureVerifier(listOf(signer.trustedKey)))

    private fun entry(id: String, license: String? = "CC-BY-NC-4.0"): ManifestEntry =
        ManifestEntry(id, license, "1.0.0", SHA, listOf("any"), MODEL_SIZE, listOf(DEFAULT_URL), "onnx")

    private fun body(vararg entries: ManifestEntry): ManifestBody = ManifestBody(RELEASE, entries.toList())

    @Test
    fun `valid signature produces descriptors`() {
        val manifest = loader.load(signer.json(signer.envelope(body(entry("nllb")))))
        assertEquals(1, manifest.descriptors.size)
        assertEquals("nllb", manifest.descriptors.first().id)
    }

    @Test
    fun `tampered vendor manifest is rejected before any descriptor is produced`() {
        val envelope = signer.envelope(body(entry("nllb", license = "CC-BY-NC-4.0")))
        val tampered = envelope.copy(payload = envelope.payload.replace("CC-BY-NC-4.0", "Apache-2.0"))
        val error = assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(signer.json(tampered))
        }
        assertEquals(envelope.manifestId, error.manifestId)
    }

    @Test
    fun `absent signature is rejected`() {
        val envelope = signer.envelope(body(entry("nllb"))).copy(signature = null)
        assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(signer.json(envelope))
        }
    }

    @Test
    fun `a host key cannot sign the vendor manifest`() {
        val hostSigner = TestManifestSigner()
        val forged = hostSigner.envelope(
            body(entry("nllb")),
            signingKeyPair = hostSigner.keyPair,
            manifestId = TestManifestSigner.MANIFEST_ID
        )
        assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(hostSigner.json(forged))
        }
    }

    @Test
    fun `pinned vendor key is a usable EC public key`() {
        assertTrue(VendorReleaseKeys.pinned.isNotEmpty())
        assertEquals("EC", VendorReleaseKeys.pinned.first().publicKey().algorithm)
    }

    private companion object {
        const val RELEASE = "2026.10.0"
        const val DEFAULT_URL = "https://cdn.example.invalid/models/a"
        const val MODEL_SIZE = 1024L
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
