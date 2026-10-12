package hk.edu.hkmu.models.store

import java.io.File

/** Phase of a [ModelStore] transfer, emitted as part of [DownloadProgress]. */
enum class DownloadPhase {
    /** The transfer is being prepared (license/network/disk gates, resume offset resolution). */
    Starting,

    /** Body bytes are being received. */
    Downloading,

    /** All bytes are on disk and the sha256 is being verified. */
    Verifying,

    /** The verified artifact was atomically moved into its final path. */
    Completed,

    /** A pre-provisioned artifact was verified and installed without any network use. */
    Imported
}

/**
 * A single progress sample emitted by [ModelStore.download].
 *
 * The stream always ends with a terminal sample ([DownloadPhase.Completed] or
 * [DownloadPhase.Imported]) carrying [installedFile]; because [ModelStore.download] uses a
 * `channelFlow`, a slow collector applies backpressure to the transfer rather than buffering
 * without bound.
 */
data class DownloadProgress(
    val modelId: String,
    val phase: DownloadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val activeUrl: String? = null,
    val attempt: Int = 1,
    val installedFile: File? = null
) {
    /** Downloaded fraction in `[0.0, 1.0]`; `0.0` when the total size is unknown. */
    val fraction: Double
        get() = if (totalBytes <= 0L) {
            0.0
        } else {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
        }

    /** True for a terminal sample (the artifact is installed and verified). */
    val isTerminal: Boolean
        get() = phase == DownloadPhase.Completed || phase == DownloadPhase.Imported
}
