package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestParsingTest {
    private val signer = TestManifestSigner()
    private val loader = SignedManifestLoader(ManifestSignatureVerifier(listOf(signer.trustedKey)))

    private fun load(vararg entries: ManifestEntry): SignedManifest =
        loader.load(signer.json(signer.envelope(ManifestBody(RELEASE, entries.toList()))))

    @Suppress("LongParameterList")
    private fun entry(
        id: String,
        format: String = "onnx",
        license: String? = "Apache-2.0",
        version: String = "1.0.0",
        sha256: String = SHA,
        abi: List<String> = listOf(ABI_ARM64),
        sizeBytes: Long = MODEL_SIZE,
        urls: List<String> = listOf(DEFAULT_URL)
    ): ManifestEntry = ManifestEntry(id, license, version, sha256, abi, sizeBytes, urls, format)

    @Test
    fun `parses valid signed manifest including task and onnx models`() {
        val manifest = load(
            entry(id = "landmarker", format = "task", abi = listOf("any")),
            entry(id = "nllb", format = "onnx", abi = listOf("any"), license = "CC-BY-NC-4.0"),
            entry(id = "upper", sha256 = UPPER_SHA)
        )
        assertEquals(3, manifest.descriptors.size)
        assertEquals(RELEASE, manifest.release)
        val landmarker = checkNotNull(manifest.descriptor("landmarker"))
        assertEquals(ModelFormat.Task, landmarker.format)
        assertEquals(setOf(Abi.Any), landmarker.abi)
        assertEquals(LicenseKind.Permissive, landmarker.license.kind)
        assertTrue(landmarker.source is ModelSource.Vendor)
        val nllb = checkNotNull(manifest.descriptor("nllb"))
        assertEquals(LicenseKind.NonCommercial, nllb.license.kind)
        assertEquals(SHA, nllb.sha256)
        assertEquals(UPPER_SHA.lowercase(), checkNotNull(manifest.descriptor("upper")).sha256)
    }

    @Test
    fun `missing license is ManifestInvalid`() {
        val error = assertThrows(AiAndroidException.ManifestInvalid::class.java) {
            load(entry(id = "nllb", license = null))
        }
        assertEquals("nllb", error.manifestId)
        assertTrue(error.reason.contains("license"))
    }

    @Test
    fun `unknown format is ManifestInvalid`() {
        val error = assertThrows(AiAndroidException.ManifestInvalid::class.java) {
            load(entry(id = "mystery", format = "safetensors"))
        }
        assertEquals("mystery", error.manifestId)
        assertTrue(error.reason.contains("format"))
    }

    @Test
    fun `malformed entries are rejected as ManifestInvalid`() {
        val cases = listOf(
            entry(id = "bad-license", license = "WTFPL") to "license",
            entry(id = "unknown-abi", abi = listOf("mips")) to "abi",
            entry(id = "empty-abi", abi = emptyList()) to "abi",
            entry(id = "abi-combo", abi = listOf("any", ABI_ARM64)) to "abi",
            entry(id = "bad-sha", sha256 = "deadbeef") to "sha256",
            entry(id = "empty-urls", urls = emptyList()) to "url",
            entry(id = "bad-size", sizeBytes = 0L) to "size",
            entry(id = "bad-version", version = "v1") to "version"
        )
        cases.forEach { (entry, fragment) ->
            val error = assertThrows(AiAndroidException.ManifestInvalid::class.java) { load(entry) }
            assertTrue(
                "reason for ${entry.id} should mention '$fragment': ${error.reason}",
                error.reason.contains(fragment)
            )
        }
    }

    @Test
    fun `descriptor reports abi compatibility`() {
        val manifest = load(
            entry(id = "onnx", abi = listOf("any")),
            entry(id = "tflite", abi = listOf(ABI_ARM64, "x86_64"))
        )
        val onnx = checkNotNull(manifest.descriptor("onnx"))
        val tflite = checkNotNull(manifest.descriptor("tflite"))
        assertTrue(onnx.supportsAbis(setOf(Abi.ArmeabiV7a)))
        assertTrue(tflite.supportsAbis(setOf(Abi.X86_64)))
        assertFalse(tflite.supportsAbis(setOf(Abi.ArmeabiV7a)))
    }

    private companion object {
        const val RELEASE = "2026.10.0"
        const val ABI_ARM64 = "arm64-v8a"
        const val DEFAULT_URL = "https://cdn.example.invalid/models/a"
        const val MODEL_SIZE = 1024L
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val UPPER_SHA = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"
    }
}
