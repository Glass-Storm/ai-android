package hk.edu.hkmu.models.store

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.models.manifest.ModelDescriptor
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Fake-server suite for [ModelStore]. Uses an in-test JDK HTTP server
 * ([FakeHttpServer]) rather than `testkit`'s shared server, so todo 11 owns its seam.
 */
class ModelStoreDownloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val servers = mutableListOf<FakeHttpServer>()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    private fun server(): FakeHttpServer = FakeHttpServer().start().also { servers += it }

    @Suppress("LongParameterList")
    private fun store(
        root: File,
        policy: LicensePolicy = AllowAllLicensePolicy,
        network: NetworkMonitor = NetworkMonitor.Unmetered,
        metered: MeteredNetworkPolicy = MeteredNetworkPolicy.BlockDownloads,
        probe: DiskSpaceProbe = DiskSpaceProbe.Real,
        config: ModelStoreConfig = DEFAULT_CONFIG
    ): ModelStore = ModelStore(root, policy, network, metered, probe, config)

    private fun ModelStore.collect(
        descriptor: ModelDescriptor,
        intent: UsageIntent = UsageIntent.SelfUse
    ): List<DownloadProgress> = runBlocking { download(descriptor, intent).toList() }

    private fun ModelStore.install(
        descriptor: ModelDescriptor,
        intent: UsageIntent = UsageIntent.SelfUse
    ): DownloadProgress = runBlocking { download(descriptor, intent).last() }

    @Test
    fun `downloads a full 200 body and verifies sha256`() {
        val body = TestModelFixtures.body()
        val sha = TestModelFixtures.sha256(body)
        val http = server().route("/m.onnx", Respond.bytes(body, supportsRange = false))
        val descriptor = TestModelFixtures.vendorDescriptor(urls = listOf(http.url("/m.onnx")), sha256 = sha)
        val store = store(temp.newFolder())

        val last = store.install(descriptor)

        assertEquals(DownloadPhase.Completed, last.phase)
        assertTrue(last.installedFile!!.isFile)
        assertEquals(sha, Sha256.ofFile(last.installedFile!!))
        assertTrue(store.isInstalled(descriptor))
        assertEquals(1, http.requestCount())
        assertNull(http.requests.single().rangeStart)
    }

    @Test
    fun `resumes with a 206 range request from a persisted partial`() {
        val body = TestModelFixtures.body()
        val sha = TestModelFixtures.sha256(body)
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(urls = listOf(http.url("/m.onnx")), sha256 = sha)
        val store = store(temp.newFolder())
        seedPartial(store, descriptor, body, RESUME_AT, http.url("/m.onnx"))

        val last = store.install(descriptor)

        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(RESUME_AT.toLong(), http.requests.single().rangeStart!!)
        assertEquals(sha, Sha256.ofFile(store.installedFile(descriptor)))
    }

    @Test
    fun `fails typed when the only mirror returns 500`() {
        val http = server().route("/m.onnx", Respond.status(500))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(TestModelFixtures.body())
        )
        val store = store(temp.newFolder())

        assertThrows(DownloadException.MirrorExhausted::class.java) { store.collect(descriptor) }
        assertFalse(store.installedFile(descriptor).exists())
    }

    @Test
    fun `fails typed when the mirror times out`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body, delayMillis = 1_500L))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder(), config = DEFAULT_CONFIG.copy(readTimeoutMillis = TIMEOUT_MILLIS))

        val error = assertThrows(DownloadException.MirrorExhausted::class.java) { store.collect(descriptor) }
        assertTrue(error.failures.isNotEmpty())
        assertFalse(store.installedFile(descriptor).exists())
    }

    @Test
    fun `fails typed when the mirror truncates the body`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.truncate(body, limit = TestModelFixtures.SIZE / 2))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())

        assertThrows(DownloadException.MirrorExhausted::class.java) { store.collect(descriptor) }
        assertFalse(store.installedFile(descriptor).exists())
    }

    @Test
    fun `checksum mismatch deletes the partial and throws ChecksumMismatch`() {
        val expected = TestModelFixtures.body(seed = 0)
        val corrupted = TestModelFixtures.body(seed = 3)
        val http = server().route("/m.onnx", Respond.bytes(corrupted))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(expected)
        )
        val store = store(temp.newFolder())
        val finalFile = store.installedFile(descriptor)

        val error = assertThrows(AiAndroidException.ChecksumMismatch::class.java) { store.collect(descriptor) }

        assertEquals(descriptor.sha256, error.expectedSha256)
        assertEquals(TestModelFixtures.sha256(corrupted), error.actualSha256)
        assertFalse(finalFile.exists())
        assertFalse(PartialFiles.partFile(finalFile).exists())
        assertFalse(PartialFiles.metaFile(finalFile).exists())
    }

    @Test
    fun `resumes across a process restart using persisted progress`() {
        val body = TestModelFixtures.body()
        val sha = TestModelFixtures.sha256(body)
        val first = server().route("/m.onnx", Respond.truncate(body, limit = RESUME_AT))
        val descriptorBefore = TestModelFixtures.vendorDescriptor(urls = listOf(first.url("/m.onnx")), sha256 = sha)
        val root = temp.newFolder()
        val storeBefore = store(root)

        assertThrows(DownloadException.MirrorExhausted::class.java) { storeBefore.collect(descriptorBefore) }
        assertTrue(PartialFiles.partFile(storeBefore.installedFile(descriptorBefore)).isFile)

        val second = server().route("/m.onnx", Respond.bytes(body))
        val descriptorAfter = TestModelFixtures.vendorDescriptor(urls = listOf(second.url("/m.onnx")), sha256 = sha)
        val storeAfter = store(root)

        val last = storeAfter.install(descriptorAfter)

        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(RESUME_AT.toLong(), second.requests.single().rangeStart!!)
        assertEquals(sha, Sha256.ofFile(storeAfter.installedFile(descriptorAfter)))
    }

    @Test
    fun `is offline then imports a pre-provisioned file without network`() {
        val body = TestModelFixtures.body()
        val sha = TestModelFixtures.sha256(body)
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(urls = listOf(http.url("/m.onnx")), sha256 = sha)
        val store = store(temp.newFolder(), network = NetworkMonitor { NetworkState.Offline })

        assertThrows(DownloadException.Offline::class.java) { store.collect(descriptor) }
        assertEquals(0, http.requestCount())

        val source = temp.newFile("provisioned.onnx").apply { writeBytes(body) }
        val imported = runBlocking { store.importPreProvisioned(descriptor, source) }

        assertEquals(DownloadPhase.Imported, imported.phase)
        assertTrue(store.isInstalled(descriptor))
        assertEquals(0, http.requestCount())
    }

    @Test
    fun `deduplicates two concurrent requests for the same model`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body, chunkBytes = 4_096, chunkDelayMillis = 20L))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())

        val terminals = runBlocking {
            val first = async { store.download(descriptor).last() }
            val second = async { store.download(descriptor).last() }
            awaitAll(first, second)
        }

        assertEquals(1, http.requestCount())
        assertTrue(terminals.all { it.phase == DownloadPhase.Completed })
    }

    @Test
    fun `fails over from a 500 primary mirror to a 200 secondary`() {
        val body = TestModelFixtures.body()
        val primary = server().route("/primary.onnx", Respond.status(500))
        val secondary = server().route("/secondary.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(primary.url("/primary.onnx"), secondary.url("/secondary.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())

        val last = store.install(descriptor)

        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(1, primary.requestCount())
        assertEquals(1, secondary.requestCount())
    }

    @Test
    fun `blocks a metered download but allows it when policy permits`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )

        val blocked = store(
            temp.newFolder(),
            network = NetworkMonitor { NetworkState.Metered },
            metered = MeteredNetworkPolicy.BlockDownloads
        )
        assertThrows(DownloadException.MeteredNetworkBlocked::class.java) { blocked.collect(descriptor) }
        assertEquals(0, http.requestCount())

        val allowed = store(
            temp.newFolder(),
            network = NetworkMonitor { NetworkState.Metered },
            metered = MeteredNetworkPolicy.AllowDownloads
        )
        assertEquals(DownloadPhase.Completed, allowed.install(descriptor).phase)
    }

    @Test
    fun `refuses an NC vendor model under Commercial before any bytes land`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body),
            license = TestModelFixtures.nonCommercialLicense
        )
        val store = store(temp.newFolder(), policy = TestLicensePolicy())

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            store.collect(descriptor, UsageIntent.Commercial)
        }

        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals(0, http.requestCount())
        assertNoArtifacts(store, descriptor)
    }

    @Test
    fun `refuses a BYO UnknownLicense model under Commercial before any bytes land`() {
        val body = TestModelFixtures.body()
        val http = server().route("/byo.tflite", Respond.bytes(body))
        val descriptor = TestModelFixtures.userProvidedDescriptor(
            id = "byo-csl",
            urls = listOf(http.url("/byo.tflite")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder(), policy = TestLicensePolicy())

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            store.collect(descriptor, UsageIntent.Commercial)
        }

        assertEquals(LicenseKind.UnknownLicense, error.licenseKind)
        assertEquals(0, http.requestCount())
        assertNoArtifacts(store, descriptor)
    }

    @Test
    fun `does not re-download when the installed checksum already matches`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())

        assertEquals(DownloadPhase.Completed, store.install(descriptor).phase)
        assertEquals(DownloadPhase.Completed, store.install(descriptor).phase)

        assertEquals(1, http.requestCount())
    }

    @Test
    fun `fails the disk preflight before any request when space is insufficient`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder(), probe = DiskSpaceProbe { 1L })

        assertThrows(DownloadException.InsufficientDiskSpace::class.java) { store.collect(descriptor) }
        assertEquals(0, http.requestCount())
    }

    @Test
    fun `cancels an in-flight download and cleans the partial`() {
        val body = TestModelFixtures.body()
        val http = server().route("/m.onnx", Respond.bytes(body, chunkBytes = 4_096, chunkDelayMillis = 60L))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())
        val finalFile = store.installedFile(descriptor)

        runBlocking {
            val job = launch { store.download(descriptor).collect { } }
            delay(CANCEL_AFTER_MILLIS)
            job.cancelAndJoin()
        }

        assertFalse(finalFile.exists())
        assertFalse(PartialFiles.partFile(finalFile).exists())
        assertFalse(PartialFiles.metaFile(finalFile).exists())
    }

    private fun seedPartial(
        store: ModelStore,
        descriptor: ModelDescriptor,
        body: ByteArray,
        prefixBytes: Int,
        sourceUrl: String
    ) {
        val finalFile = store.installedFile(descriptor)
        val part = PartialFiles.partFile(finalFile)
        part.parentFile!!.mkdirs()
        part.writeBytes(body.copyOfRange(0, prefixBytes))
        PartialFiles.writeState(
            PartialFiles.metaFile(finalFile),
            PartialState(descriptor.id, descriptor.sha256, descriptor.sizeBytes, sourceUrl)
        )
    }

    private fun assertNoArtifacts(store: ModelStore, descriptor: ModelDescriptor) {
        val finalFile = store.installedFile(descriptor)
        assertFalse(finalFile.exists())
        assertFalse(PartialFiles.partFile(finalFile).exists())
    }

    private companion object {
        const val RESUME_AT = 20 * 1024
        const val TIMEOUT_MILLIS = 300
        const val CANCEL_AFTER_MILLIS = 300L
        val DEFAULT_CONFIG = ModelStoreConfig(
            connectTimeoutMillis = 3_000,
            readTimeoutMillis = 5_000,
            progressIntervalBytes = 4_096,
            diskSafetyMarginBytes = 0,
            copyBufferBytes = 8_192
        )
    }
}
