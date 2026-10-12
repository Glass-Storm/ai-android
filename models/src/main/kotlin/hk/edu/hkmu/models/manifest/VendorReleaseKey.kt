package hk.edu.hkmu.models.manifest

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** A pinned vendor release public key (X.509 SubjectPublicKeyInfo, base64/DER). */
@JvmInline
value class VendorReleaseKey(val encoded: String) {
    /** Decodes this key for use with [java.security.Signature]. */
    fun publicKey(): PublicKey {
        val der = Base64.getDecoder().decode(encoded)
        return KeyFactory.getInstance(ECDSA_ALGORITHM).generatePublic(X509EncodedKeySpec(der))
    }

    private companion object {
        const val ECDSA_ALGORITHM = "EC"
    }
}
