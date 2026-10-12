package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException
import java.security.Signature
import java.util.Base64

/**
 * Verifies a manifest payload's detached signature against pinned trusted keys.
 *
 * Only keys supplied at construction are trusted; production passes [VendorReleaseKeys.pinned]. A
 * signature made by any other key (e.g. a host key) fails verification, and an absent or undecodable
 * signature fails closed.
 */
class ManifestSignatureVerifier(
    private val trustedKeys: List<VendorReleaseKey> = VendorReleaseKeys.pinned
) {
    /**
     * Verifies [signatureBase64] over the raw [payload] bytes.
     *
     * @throws AiAndroidException.ManifestSignatureInvalid when the signature is absent, undecodable,
     *   or does not verify under any trusted key.
     */
    fun verify(manifestId: String, payload: String, signatureBase64: String?) {
        val signatureBytes = signatureBase64
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?: throw AiAndroidException.ManifestSignatureInvalid(manifestId)

        val message = payload.toByteArray(Charsets.UTF_8)
        if (trustedKeys.none { isVerified(it, message, signatureBytes) }) {
            throw AiAndroidException.ManifestSignatureInvalid(manifestId)
        }
    }

    private fun isVerified(key: VendorReleaseKey, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(key.publicKey())
        verifier.update(message)
        verifier.verify(signature)
    }.getOrDefault(false)

    private companion object {
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
