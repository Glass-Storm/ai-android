package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Manual-QA (Tier A) probes. The assertions are the real contract; the `println` lines emit the
 * observable values captured into the task evidence log.
 */
class ManualQaProbeTest {
    private val signer = TestManifestSigner()
    private val loader = SignedManifestLoader(ManifestSignatureVerifier(listOf(signer.trustedKey)))

    private fun entry(id: String, format: String, license: String): ManifestEntry = ManifestEntry(
        id,
        license,
        "2026.10.12",
        SHA,
        listOf("any"),
        MODEL_SIZE,
        listOf("https://cdn.example.invalid/models/$id"),
        format
    )

    private fun body(vararg entries: ManifestEntry): ManifestBody = ManifestBody(RELEASE, entries.toList())

    @Test
    fun `manual qa happy path parses a signed task landmark asset`() {
        val manifest = loader.load(signer.json(signer.envelope(body(entry("landmarker", "task", "Apache-2.0")))))
        val descriptor = manifest.descriptors.single()
        println("QA-HAPPY manifest=${manifest.manifestId} release=${manifest.release}")
        println("QA-HAPPY descriptors=${manifest.descriptors.size}")
        println(
            "QA-HAPPY descriptor id=${descriptor.id} format=${descriptor.format} license=${descriptor.license.kind} " +
                "version=${descriptor.version.raw} abi=${descriptor.abi} source=${descriptor.source}"
        )
        assertEquals(1, manifest.descriptors.size)
        assertEquals(ModelFormat.Task, descriptor.format)
    }

    @Test
    fun `manual qa tampered vendor manifest is rejected`() {
        val envelope = signer.envelope(body(entry("nllb", "onnx", "CC-BY-NC-4.0")))
        val tampered = envelope.copy(payload = envelope.payload.replace("CC-BY-NC-4.0", "Apache-2.0"))
        val error = assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(signer.json(tampered))
        }
        println("QA-TAMPER ${error::class.simpleName} manifestId=${error.manifestId} (no descriptor produced)")
        assertEquals(envelope.manifestId, error.manifestId)
    }

    @Test
    fun `manual qa unknown format is rejected`() {
        val error = assertThrows(AiAndroidException.ManifestInvalid::class.java) {
            loader.load(signer.json(signer.envelope(body(entry("mystery", "safetensors", "MIT")))))
        }
        println("QA-BADFORMAT ${error::class.simpleName} id=${error.manifestId} reason=${error.reason}")
        assertEquals("mystery", error.manifestId)
    }

    @Test
    fun `manual qa byo csl model registers as UnknownLicense`() {
        val descriptor = UserProvidedModelRegistrar().register(
            UserProvidedModel(
                "csl-byo",
                declaredLicense = "Apache-2.0",
                version = "1.0.0",
                sha256 = SHA,
                abi = setOf(Abi.Arm64V8a, Abi.X86_64),
                sizeBytes = MODEL_SIZE,
                urls = listOf("https://host.example.invalid/csl.tflite"),
                format = ModelFormat.Tflite
            )
        )
        println(
            "QA-BYO id=${descriptor.id} license=${descriptor.license.kind} declared=${descriptor.license.spdxId} " +
                "source=${descriptor.source} format=${descriptor.format}"
        )
        assertEquals(LicenseKind.UnknownLicense, descriptor.license.kind)
    }

    private companion object {
        const val RELEASE = "2026.10.0"
        const val MODEL_SIZE = 1024L
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
