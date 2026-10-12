package hk.edu.hkmu.models.store

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.models.manifest.ModelDescriptor
import java.io.File
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Manual-QA (Tier A) probes for the downloader. Assertions are the real contract; the `println`
 * lines emit the observable values captured into `.omo/evidence/task-11-ondevice-sign-asr-tts.log`.
 */
class ManualQaProbeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val servers = mutableListOf<FakeHttpServer>()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    private fun server(): FakeHttpServer = FakeHttpServer().start().also { servers += it }

    private fun store(
        root: File,
        policy: hk.edu.hkmu.core.license.LicensePolicy = AllowAllLicensePolicy,
        network: NetworkMonitor = NetworkMonitor.Unmetered,
        metered: MeteredNetworkPolicy = MeteredNetworkPolicy.BlockDownloads
    ): ModelStore = ModelStore(root, policy, network, metered, DiskSpaceProbe.Real, CONFIG)

    private fun ModelStore.collect(
        descriptor: ModelDescriptor,
        intent: UsageIntent = UsageIntent.SelfUse
    ): List<DownloadProgress> = runBlocking { download(descriptor, intent).toList() }

    @Test
    fun `manual qa 206 resume completes and verifies sha256`() {
        val body = TestModelFixtures.body()
        val sha = TestModelFixtures.sha256(body)
        val http = server().route("/m.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(urls = listOf(http.url("/m.onnx")), sha256 = sha)
        val store = store(temp.newFolder())
        val finalFile = store.installedFile(descriptor)
        val part = PartialFiles.partFile(finalFile)
        part.parentFile!!.mkdirs()
        part.writeBytes(body.copyOfRange(0, RESUME_AT))
        PartialFiles.writeState(
            PartialFiles.metaFile(finalFile),
            PartialState(descriptor.id, sha, descriptor.sizeBytes, http.url("/m.onnx"))
        )

        val last = runBlocking { store.download(descriptor).last() }
        val verified = Sha256.ofFile(finalFile)

        println("QA-RESUME phase=${last.phase} range=${http.requests.single().rangeStart} size=${last.totalBytes}")
        println("QA-RESUME installed=${finalFile.name} sha256=$verified verified=${verified == sha}")
        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(RESUME_AT.toLong(), http.requests.single().rangeStart!!)
        assertEquals(sha, verified)
    }

    @Test
    fun `manual qa checksum mismatch deletes the partial`() {
        val expected = TestModelFixtures.body(seed = 0)
        val corrupted = TestModelFixtures.body(seed = 9)
        val http = server().route("/m.onnx", Respond.bytes(corrupted))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(http.url("/m.onnx")),
            sha256 = TestModelFixtures.sha256(expected)
        )
        val store = store(temp.newFolder())
        val finalFile = store.installedFile(descriptor)

        val error = assertThrows(AiAndroidException.ChecksumMismatch::class.java) { store.collect(descriptor) }

        println(
            "QA-CHECKSUM type=${error::class.simpleName} expected=${error.expectedSha256} " +
                "actual=${error.actualSha256}"
        )
        println(
            "QA-CHECKSUM finalExists=${finalFile.exists()} partExists=${PartialFiles.partFile(finalFile).exists()} " +
                "metaExists=${PartialFiles.metaFile(finalFile).exists()}"
        )
        assertFalse(finalFile.exists())
        assertFalse(PartialFiles.partFile(finalFile).exists())
    }

    @Test
    fun `manual qa mirror failover primary 500 to secondary 200`() {
        val body = TestModelFixtures.body()
        val primary = server().route("/primary.onnx", Respond.status(500))
        val secondary = server().route("/secondary.onnx", Respond.bytes(body))
        val descriptor = TestModelFixtures.vendorDescriptor(
            urls = listOf(primary.url("/primary.onnx"), secondary.url("/secondary.onnx")),
            sha256 = TestModelFixtures.sha256(body)
        )
        val store = store(temp.newFolder())

        val last = runBlocking { store.download(descriptor).last() }

        println(
            "QA-MIRROR primary=${primary.requestCount()}x$STATUS_500 " +
                "secondary=${secondary.requestCount()}x$STATUS_200 phase=${last.phase} " +
                "installed=${store.isInstalled(descriptor)}"
        )
        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(1, primary.requestCount())
        assertEquals(1, secondary.requestCount())
    }

    @Test
    fun `manual qa metered download refused under policy`() {
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

        val error = assertThrows(DownloadException.MeteredNetworkBlocked::class.java) { blocked.collect(descriptor) }

        println("QA-METERED state=${NetworkState.Metered} policy=BlockDownloads type=${error::class.simpleName}")
        println("QA-METERED requests=${http.requestCount()} installed=${blocked.installedFile(descriptor).exists()}")
        assertEquals(0, http.requestCount())
        assertFalse(blocked.installedFile(descriptor).exists())
    }

    @Test
    fun `manual qa nc model refused under commercial before any bytes`() {
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

        val finalFile = store.installedFile(descriptor)
        println("QA-LICENSE kind=${error.licenseKind} intent=${error.intent} type=${error::class.simpleName}")
        println(
            "QA-LICENSE requests=${http.requestCount()} finalExists=${finalFile.exists()} " +
                "partExists=${PartialFiles.partFile(finalFile).exists()}"
        )
        assertTrue(http.requestCount() == 0)
        assertFalse(finalFile.exists())
    }

    private companion object {
        const val RESUME_AT = 20 * 1024
        const val STATUS_500 = 500
        const val STATUS_200 = 200
        val CONFIG = ModelStoreConfig(
            connectTimeoutMillis = 3_000,
            readTimeoutMillis = 5_000,
            progressIntervalBytes = 4_096,
            diskSafetyMarginBytes = 0,
            copyBufferBytes = 8_192
        )
    }
}
