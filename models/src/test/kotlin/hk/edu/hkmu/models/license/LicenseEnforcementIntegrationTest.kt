@file:Suppress("MagicNumber")

package hk.edu.hkmu.models.license

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ManifestBody
import hk.edu.hkmu.models.manifest.ModelDescriptor
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.models.manifest.UserProvidedModelRegistrar
import hk.edu.hkmu.models.store.DownloadPhase
import hk.edu.hkmu.models.store.FakeHttpServer
import hk.edu.hkmu.models.store.ModelStore
import hk.edu.hkmu.models.store.ModelStoreConfig
import hk.edu.hkmu.models.store.PartialFiles
import hk.edu.hkmu.models.store.Respond
import hk.edu.hkmu.models.store.TestModelFixtures
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
 * End-to-end proof that the concrete [DefaultLicensePolicy] is enforced by the **shared load paths**
 * — the core model registry (todo 9) and the downloader (todo 11) — and not merely callable as an
 * object.
 *
 * Every descriptor under test is produced by the real trust pipeline ([LicenseTestFixtures.manifest]
 * -> signature-verified vendor manifest, or `UserProvidedModelRegistrar` for BYO), so the license the
 * policy evaluates is the one production would receive.
 */
class LicenseEnforcementIntegrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val servers = mutableListOf<FakeHttpServer>()
    private val signer = LicenseTestFixtures.signer()
    private val loader = LicenseTestFixtures.loaderFor(signer)

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    // --- registry: the loader must never be invoked for a refused model --------------------------

    @Test
    fun `commercial registry load of the nc nllb model is blocked before the loader runs`() {
        val nllb = vendorDescriptor("nllb", "CC-BY-NC-4.0")
        val recording = RecordingLoader()
        val registry = registry(recording, UsageIntent.Commercial, nllb)

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.load(nllb.id) }

        assertEquals(nllb.id, error.modelId)
        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals(UsageIntent.Commercial, error.intent)
        assertEquals("the loader must never be invoked", 0, recording.loaded.size)
        assertFalse(registry.isResident(nllb.id))
    }

    @Test
    fun `commercial registry load of the c-uda wlasl sign model is blocked before the loader runs`() {
        val wlasl = vendorDescriptor("wlasl-tgcn", "C-UDA-1.0")
        val recording = RecordingLoader()
        val registry = registry(recording, UsageIntent.Commercial, wlasl)

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.load(wlasl.id) }

        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals(0, recording.loaded.size)
        assertFalse(registry.isResident(wlasl.id))
    }

    @Test
    fun `commercial registry load of a byo unknown-license model is blocked before the loader runs`() {
        val byo = byoDescriptor("csl-byo")
        assertEquals(LicenseKind.UnknownLicense, byo.license.kind)
        val recording = RecordingLoader()
        val registry = registry(recording, UsageIntent.Commercial, byo)

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.load(byo.id) }

        assertEquals(LicenseKind.UnknownLicense, error.licenseKind)
        assertEquals("a BYO model must never reach the loader under Commercial", 0, recording.loaded.size)
    }

    @Test
    fun `self-use registry load of the nc nllb model reaches the loader`() {
        val nllb = vendorDescriptor("nllb", "CC-BY-NC-4.0")
        val recording = RecordingLoader()
        val registry = registry(recording, UsageIntent.SelfUse, nllb)

        val lease = registry.acquire(nllb.id)

        assertEquals(listOf(nllb.id), recording.loaded)
        assertEquals(1, registry.refCount(nllb.id))
        lease.close()
    }

    // --- downloader: a refused model must issue no request and write no bytes --------------------

    @Test
    fun `commercial download of the nc model is blocked before any request`() {
        val body = TestModelFixtures.body()
        val http = server().route("/nllb.onnx", Respond.bytes(body))
        val nllb = signedDescriptor(http, "/nllb.onnx", body, "nllb", "CC-BY-NC-4.0")
        val store = store(temp.newFolder())

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            runBlocking { store.download(nllb, UsageIntent.Commercial).toList() }
        }

        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals("no HTTP request may be issued", 0, http.requestCount())
        assertNoArtifacts(store, nllb)
    }

    @Test
    fun `commercial download of a byo unknown-license model is blocked before any request`() {
        val body = TestModelFixtures.body()
        val http = server().route("/byo.tflite", Respond.bytes(body))
        val byo = byoDescriptor("csl-byo", url = http.url("/byo.tflite"), sha256 = TestModelFixtures.sha256(body))
        val store = store(temp.newFolder())

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            runBlocking { store.download(byo, UsageIntent.Commercial).toList() }
        }

        assertEquals(LicenseKind.UnknownLicense, error.licenseKind)
        assertEquals(0, http.requestCount())
        assertNoArtifacts(store, byo)
    }

    @Test
    fun `commercial download of the permissive asl model is allowed`() {
        val body = TestModelFixtures.body()
        val http = server().route("/asl.onnx", Respond.bytes(body))
        val asl = signedDescriptor(http, "/asl.onnx", body, "asl-fingerspelling", "Apache-2.0")
        val store = store(temp.newFolder())

        val last = runBlocking { store.download(asl, UsageIntent.Commercial).last() }

        assertEquals(DownloadPhase.Completed, last.phase)
        assertEquals(1, http.requestCount())
        assertTrue(store.isInstalled(asl))
    }

    // --- adversarial: the relabel attack must be impossible --------------------------------------

    @Test
    fun `a tampered vendor manifest cannot relabel an nc model as permissive`() {
        val envelope = signer.envelope(
            ManifestBody(LicenseTestFixtures.RELEASE, listOf(LicenseTestFixtures.entry("nllb", "CC-BY-NC-4.0")))
        )
        val tampered = envelope.copy(payload = envelope.payload.replace("CC-BY-NC-4.0", "Apache-2.0"))

        val error = assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(signer.json(tampered))
        }
        assertEquals(envelope.manifestId, error.manifestId)

        // The relabel is exactly the change that would make the NC model pass under Commercial; the
        // signature check is what denies it, so no descriptor with a forged permissive license exists.
        val forged = ModelLicense(LicenseKind.Permissive, spdxId = "Apache-2.0")
        assertTrue(
            DefaultLicensePolicy().evaluate(forged, UsageIntent.Commercial) is LicenseDecision.Allowed
        )
    }

    @Test
    fun `a host key cannot sign the vendor manifest`() {
        val hostSigner = LicenseTestFixtures.signer()
        val forged = hostSigner.envelope(
            ManifestBody(LicenseTestFixtures.RELEASE, listOf(LicenseTestFixtures.entry("nllb", "Apache-2.0"))),
            signingKeyPair = hostSigner.keyPair
        )

        assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(hostSigner.json(forged))
        }
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun registry(
        recording: RecordingLoader,
        intent: UsageIntent,
        descriptor: ModelDescriptor
    ): ModelRegistry<RecordingHandle> {
        val registry = ModelRegistry(recording, DefaultLicensePolicy(), intent, CAPACITY)
        registry.register(ModelRegistration(descriptor.id, descriptor.license))
        return registry
    }

    private fun server(): FakeHttpServer = FakeHttpServer().start().also { servers += it }

    private fun store(root: File): ModelStore =
        ModelStore(root, DefaultLicensePolicy(), config = ModelStoreConfig(diskSafetyMarginBytes = 0L))

    private fun vendorDescriptor(id: String, license: String): ModelDescriptor {
        val manifest = LicenseTestFixtures.manifest(signer, loader, LicenseTestFixtures.entry(id, license))
        return requireNotNull(manifest.descriptor(id))
    }

    private fun signedDescriptor(
        http: FakeHttpServer,
        path: String,
        body: ByteArray,
        id: String,
        license: String
    ): ModelDescriptor {
        val entry = LicenseTestFixtures.entry(
            id = id,
            license = license,
            url = http.url(path),
            sha256 = TestModelFixtures.sha256(body),
            sizeBytes = body.size.toLong()
        )
        val manifest = LicenseTestFixtures.manifest(signer, loader, entry)
        return requireNotNull(manifest.descriptor(id))
    }

    private fun byoDescriptor(
        id: String,
        url: String = LicenseTestFixtures.DEFAULT_URL,
        sha256: String = LicenseTestFixtures.PLACEHOLDER_SHA
    ): ModelDescriptor = UserProvidedModelRegistrar().register(
        UserProvidedModel(
            id = id,
            declaredLicense = "Apache-2.0",
            version = LicenseTestFixtures.VERSION,
            sha256 = sha256,
            abi = setOf(Abi.Any),
            sizeBytes = TestModelFixtures.SIZE.toLong(),
            urls = listOf(url),
            format = ModelFormat.Tflite
        )
    )

    private fun assertNoArtifacts(store: ModelStore, descriptor: ModelDescriptor) {
        val finalFile = store.installedFile(descriptor)
        assertFalse(finalFile.exists())
        assertFalse(PartialFiles.partFile(finalFile).exists())
    }

    private companion object {
        const val CAPACITY = 4
    }
}
