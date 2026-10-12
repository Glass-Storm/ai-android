package hk.edu.hkmu.models.manifest

/**
 * Pinned vendor release keys.
 *
 * The ECDSA P-256 public key below is compiled into the library; the matching private key lives only
 * in the vendor's offline release process. A host cannot add its own key: this set is a fixed
 * constant and [ManifestSignatureVerifier] only trusts the keys handed to it (production passes
 * [pinned]).
 */
object VendorReleaseKeys {
    /**
     * Pinned vendor release key (ECDSA P-256, X.509 SPKI/DER base64). Public by design; the private
     * key is never shipped.
     */
    const val PINNED_RELEASE_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmz7zJNMKn7/2E6BPOsmBmO8ReGxWe8GuEFAQJNopBX310pb0" +
            "MBRZxaCfoCb74i0k/2eRDnvbY9Bvot2gFs0B1w=="

    /** The trusted key set used by production code. */
    val pinned: List<VendorReleaseKey> = listOf(VendorReleaseKey(PINNED_RELEASE_KEY_BASE64))
}
