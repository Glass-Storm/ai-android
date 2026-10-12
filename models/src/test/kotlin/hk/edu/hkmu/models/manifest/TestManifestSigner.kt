package hk.edu.hkmu.models.manifest

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** ECDSA P-256 signer used to mint test manifests for the signature and loader tests. */
internal class TestManifestSigner(val keyPair: KeyPair = generateKeyPair()) {
    val trustedKey: VendorReleaseKey = VendorReleaseKey(Base64.getEncoder().encodeToString(keyPair.public.encoded))

    fun envelope(
        body: ManifestBody,
        signingKeyPair: KeyPair = keyPair,
        manifestId: String = MANIFEST_ID
    ): SignedManifestEnvelope {
        val payload = ManifestCodec.json.encodeToString(ManifestBody.serializer(), body)
        return SignedManifestEnvelope(manifestId, KEY_ID, payload, sign(payload, signingKeyPair.private))
    }

    fun json(envelope: SignedManifestEnvelope): String =
        ManifestCodec.json.encodeToString(SignedManifestEnvelope.serializer(), envelope)

    private fun sign(payload: String, privateKey: PrivateKey): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey)
        update(payload.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }

    companion object {
        const val MANIFEST_ID = "vendor-release-test"
        const val KEY_ID = "test-vendor-key"

        internal fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    }
}
