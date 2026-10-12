package hk.edu.hkmu.models.store

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.models.manifest.ModelDescriptor
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The ModelStore downloader: fetches, verifies, and installs the artifact of a trust-gated
 * [ModelDescriptor] produced by the signed-manifest / BYO registration path (todo 10).
 *
 * Guarantees:
 * - **License before bytes.** [download] and [importPreProvisioned] call the injected
 *   [LicensePolicy] first and refuse with [AiAndroidException.LicenseRestricted] on
 *   [LicenseDecision.Restricted]; no HTTP request is issued and no file is written.
 * - **Never a partial in the final path.** Bytes land in `<artifact>.part` and are only moved onto
 *   the final path by an atomic rename after the sha256 matches.
 * - **No redundant transfer.** An already-installed artifact whose sha256 matches short-circuits,
 *   and concurrent requests for the same model are deduplicated by a per-model lock so exactly one
 *   transfer runs.
 * - **Resume.** A resumable partial (validated by its `.part.meta` sidecar) resumes via an HTTP
 *   `Range` request, across process restarts.
 * - **Mirror failover.** Mirrors in [ModelDescriptor.urls] are tried in order; a `5xx`, timeout, or
 *   truncated body falls over to the next mirror while retaining the partial.
 * - **Cancellation.** Cancelling the collector aborts the transfer and deletes the partial.
 *
 * @property rootDir Installation root; each model gets `<rootDir>/<id>/`.
 * @property licensePolicy Gate evaluated before any byte is written.
 * @property networkMonitor Connectivity source for the metered/offline policy.
 * @property meteredPolicy Whether metered transfers are allowed.
 * @property diskSpaceProbe Disk-space source for the preflight.
 * @property config Timeouts, buffer sizes, and the disk safety margin.
 */
class ModelStore(
    private val rootDir: File,
    private val licensePolicy: LicensePolicy,
    private val networkMonitor: NetworkMonitor = NetworkMonitor.Unmetered,
    private val meteredPolicy: MeteredNetworkPolicy = MeteredNetworkPolicy.BlockDownloads,
    private val diskSpaceProbe: DiskSpaceProbe = DiskSpaceProbe.Real,
    private val config: ModelStoreConfig = ModelStoreConfig()
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val connectionFactory = HttpConnectionFactory(config)

    /** The final install path for [descriptor] (whether or not it exists). */
    fun installedFile(descriptor: ModelDescriptor): File = PartialFiles.finalFile(rootDir, descriptor)

    /** True when [descriptor] is installed and its on-disk sha256 matches the pinned checksum. */
    fun isInstalled(descriptor: ModelDescriptor): Boolean {
        val file = installedFile(descriptor)
        return file.isFile && Sha256.ofFile(file) == descriptor.sha256
    }

    /**
     * Downloads and installs [descriptor], emitting [DownloadProgress] until the terminal
     * [DownloadPhase.Completed] sample.
     *
     * The flow is cold: no gate is evaluated, no request issued, and no byte written until it is
     * collected.
     *
     * @throws AiAndroidException.LicenseRestricted before any byte is written when the policy denies
     *   [intent].
     * @throws DownloadException.Offline / [DownloadException.MeteredNetworkBlocked] per the policy.
     * @throws DownloadException.InsufficientDiskSpace when the preflight fails.
     * @throws DownloadException.MirrorExhausted when every mirror fails.
     * @throws AiAndroidException.ChecksumMismatch when the received bytes do not match the pinned
     *   sha256 (the partial is deleted).
     */
    fun download(descriptor: ModelDescriptor, intent: UsageIntent = UsageIntent.SelfUse): Flow<DownloadProgress> =
        channelFlow {
            gateLicense(descriptor, intent)
            lockFor(descriptor.id).withLock {
                val target = installedFile(descriptor)
                if (isVerifiedFile(target, descriptor)) {
                    send(completedSample(descriptor, target, DownloadPhase.Completed))
                    return@withLock
                }
                ensureNetworkAllowed(descriptor)
                preflightDisk(descriptor)
                withContext(Dispatchers.IO) {
                    runDownload(descriptor) { send(it) }
                }
            }
        }

    /**
     * Installs a pre-provisioned [sourceFile] (an offline / side-loaded artifact) without any
     * network use.
     *
     * The license gate still runs first, and the source is verified against the pinned sha256 before
     * it is atomically moved into place, so a mismatched or restricted model never lands.
     *
     * @throws AiAndroidException.LicenseRestricted before any byte is written.
     * @throws DownloadException.PreProvisionedUnavailable when [sourceFile] is missing.
     * @throws AiAndroidException.ChecksumMismatch when the source does not match the pinned sha256.
     */
    suspend fun importPreProvisioned(
        descriptor: ModelDescriptor,
        sourceFile: File,
        intent: UsageIntent = UsageIntent.SelfUse
    ): DownloadProgress = withContext(Dispatchers.IO) {
        gateLicense(descriptor, intent)
        if (!sourceFile.isFile) {
            throw DownloadException.PreProvisionedUnavailable(
                descriptor.id,
                "source file not found: ${sourceFile.path}"
            )
        }
        val target = installedFile(descriptor)
        val actual = Sha256.ofFile(sourceFile)
        if (actual != descriptor.sha256) {
            throw AiAndroidException.ChecksumMismatch(descriptor.id, descriptor.sha256, actual)
        }
        val part = PartialFiles.partFile(target)
        val meta = PartialFiles.metaFile(target)
        try {
            part.parentFile?.mkdirs()
            sourceFile.copyTo(part, overwrite = true)
            PartialFiles.atomicMove(part, target)
            PartialFiles.deleteQuietly(meta)
        } catch (error: IOException) {
            PartialFiles.cleanup(part, meta)
            throw error
        }
        completedSample(descriptor, target, DownloadPhase.Imported)
    }

    private suspend fun runDownload(descriptor: ModelDescriptor, emit: suspend (DownloadProgress) -> Unit) {
        val target = installedFile(descriptor)
        val part = PartialFiles.partFile(target)
        val meta = PartialFiles.metaFile(target)
        try {
            val offset = PartialFiles.resumeOffset(descriptor, part, meta)
            emit(DownloadProgress(descriptor.id, DownloadPhase.Starting, offset, descriptor.sizeBytes))
            if (offset < descriptor.sizeBytes) {
                MirrorDownloader(descriptor, part, meta, connectionFactory, config).fetch(emit)
            }
            emit(DownloadProgress(descriptor.id, DownloadPhase.Verifying, descriptor.sizeBytes, descriptor.sizeBytes))
            val actual = Sha256.ofFile(part)
            if (actual != descriptor.sha256) {
                PartialFiles.cleanup(part, meta)
                throw AiAndroidException.ChecksumMismatch(descriptor.id, descriptor.sha256, actual)
            }
            PartialFiles.atomicMove(part, target)
            PartialFiles.deleteQuietly(meta)
            emit(completedSample(descriptor, target, DownloadPhase.Completed))
        } catch (cancellation: CancellationException) {
            PartialFiles.cleanup(part, meta)
            throw cancellation
        }
    }

    private fun gateLicense(descriptor: ModelDescriptor, intent: UsageIntent) {
        if (licensePolicy.evaluate(descriptor.license, intent) is LicenseDecision.Restricted) {
            throw AiAndroidException.LicenseRestricted(descriptor.id, descriptor.license.kind, intent)
        }
    }

    private fun ensureNetworkAllowed(descriptor: ModelDescriptor) {
        when (networkMonitor.state()) {
            NetworkState.Offline -> throw DownloadException.Offline(descriptor.id)
            NetworkState.Metered -> {
                if (meteredPolicy == MeteredNetworkPolicy.BlockDownloads) {
                    throw DownloadException.MeteredNetworkBlocked(descriptor.id)
                }
            }
            NetworkState.Unmetered -> Unit
        }
    }

    private fun preflightDisk(descriptor: ModelDescriptor) {
        val part = PartialFiles.partFile(installedFile(descriptor))
        val alreadyDownloaded = if (part.isFile) part.length().coerceAtMost(descriptor.sizeBytes) else 0L
        val required = descriptor.sizeBytes - alreadyDownloaded + config.diskSafetyMarginBytes
        rootDir.mkdirs()
        val usable = diskSpaceProbe.usableBytes(rootDir)
        if (usable < required) {
            throw DownloadException.InsufficientDiskSpace(descriptor.id, required, usable)
        }
    }

    private fun lockFor(modelId: String): Mutex = locks.computeIfAbsent(modelId) { Mutex() }
}

private fun isVerifiedFile(target: File, descriptor: ModelDescriptor): Boolean =
    target.isFile && Sha256.ofFile(target) == descriptor.sha256

private fun completedSample(descriptor: ModelDescriptor, file: File, phase: DownloadPhase): DownloadProgress =
    DownloadProgress(
        modelId = descriptor.id,
        phase = phase,
        downloadedBytes = descriptor.sizeBytes,
        totalBytes = descriptor.sizeBytes,
        installedFile = file
    )
