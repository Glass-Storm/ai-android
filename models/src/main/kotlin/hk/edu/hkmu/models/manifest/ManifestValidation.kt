package hk.edu.hkmu.models.manifest

import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Field-level validation shared by the signed-manifest path and the BYO registration path.
 *
 * Every failure is a typed [AiAndroidException.ManifestInvalid] carrying the offending model id so
 * callers react programmatically instead of parsing messages.
 */
internal object ManifestValidation {
    private const val SHA256_HEX_LENGTH = 64
    private val sha256Pattern = Regex("^[0-9a-fA-F]{$SHA256_HEX_LENGTH}$")

    fun requireId(id: String) {
        if (id.isBlank()) invalid(id, "model id must not be blank")
    }

    fun requireSha256(id: String, sha256: String) {
        if (!sha256Pattern.matches(sha256)) invalid(id, "sha256 must be $SHA256_HEX_LENGTH hex characters")
    }

    fun requireSize(id: String, sizeBytes: Long) {
        if (sizeBytes <= 0L) invalid(id, "sizeBytes must be positive")
    }

    fun requireUrls(id: String, urls: List<String>) {
        if (urls.isEmpty()) invalid(id, "at least one download url is required")
        val bad = urls.firstOrNull { !it.startsWith("https://") && !it.startsWith("http://") }
        if (bad != null) invalid(id, "url '$bad' must use http(s)")
    }

    fun requireAbis(id: String, abis: Set<Abi>) {
        if (abis.isEmpty()) invalid(id, "abi must not be empty; use 'any' for ABI-independent artifacts")
        if (abis.size > 1 && Abi.Any in abis) invalid(id, "abi 'any' must not be combined with concrete ABIs")
    }

    fun invalid(id: String, reason: String): Nothing = throw AiAndroidException.ManifestInvalid(id, reason)
}
