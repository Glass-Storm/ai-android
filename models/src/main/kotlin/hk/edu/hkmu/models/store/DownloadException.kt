package hk.edu.hkmu.models.store

/**
 * Downloader-layer failures, all typed so hosts react programmatically.
 *
 * These are deliberately separate from (and serialized as) [hk.edu.hkmu.core.error.AiAndroidException]:
 * the core error hierarchy is a sealed class owned by `:core`, while this downloader is a
 * `:models`-layer capability. Failures that the core taxonomy already models are reused directly:
 * [hk.edu.hkmu.core.error.AiAndroidException.LicenseRestricted] (license gate) and
 * [hk.edu.hkmu.core.error.AiAndroidException.ChecksumMismatch] (verified bytes do not match the
 * pinned sha256).
 */
sealed class DownloadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** The device is offline and the artifact is not already installed. */
    class Offline(val modelId: String) :
        DownloadException("Network is offline; cannot download model '$modelId'")

    /** The device is on a metered connection and the policy blocks metered downloads. */
    class MeteredNetworkBlocked(val modelId: String) :
        DownloadException("Metered network; download of model '$modelId' is blocked by policy")

    /** The destination volume cannot hold the remaining bytes plus the configured safety margin. */
    class InsufficientDiskSpace(
        val modelId: String,
        val requiredBytes: Long,
        val availableBytes: Long
    ) : DownloadException(
        "Insufficient disk for model '$modelId': need $requiredBytes bytes, have $availableBytes"
    )

    /** Every mirror in the descriptor's `urls` failed. */
    class MirrorExhausted(
        val modelId: String,
        val failures: List<MirrorFailure>
    ) : DownloadException(
        "All mirrors failed for model '$modelId': " +
            failures.joinToString { "${it.url} -> ${it.reason}" }
    )

    /** A pre-provisioned source file was missing or otherwise unusable. */
    class PreProvisionedUnavailable(
        val modelId: String,
        val reason: String
    ) : DownloadException("Pre-provisioned model '$modelId' unavailable: $reason")
}

/**
 * Why one mirror failed.
 *
 * @property url The attempted mirror.
 * @property reason A short, non-localized description (HTTP status or I/O cause).
 */
data class MirrorFailure(
    val url: String,
    val reason: String
)
