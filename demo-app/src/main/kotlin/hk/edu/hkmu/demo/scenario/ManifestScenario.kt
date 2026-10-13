package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.models.manifest.SignedManifestLoader

/**
 * Scenario 2: the real signed-manifest loader.
 *
 * Builds a detached-signature envelope whose signature is present but tampered, then runs the real
 * [SignedManifestLoader]. The loader decodes the envelope, verifies the detached signature over the
 * raw payload bytes against the pinned vendor keys (real ECDSA), and fails with
 * [AiAndroidException.ManifestSignatureInvalid] before any descriptor is constructed.
 */
object ManifestScenario {
    private val envelope = """
        {
          "manifestId": "demo-manifest",
          "keyId": "demo-key",
          "payload": "{\"release\":\"r1\",\"models\":[]}",
          "signature": "bm90LWEtcmVhbC1zaWduYXR1cmU="
        }
    """.trimIndent()

    /** Loads the tampered envelope and renders the typed failure. */
    fun run(): String {
        val loader = SignedManifestLoader()
        return try {
            val manifest = loader.load(envelope)
            "UNEXPECTED: manifest '${manifest.manifestId}' verified with ${manifest.descriptors.size} descriptors"
        } catch (invalid: AiAndroidException.ManifestSignatureInvalid) {
            "Real ECDSA verify path rejected the tampered signature as expected:\n" +
                "  ${invalid.javaClass.simpleName}: ${invalid.message}"
        }
    }
}
