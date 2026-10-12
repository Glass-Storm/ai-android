@file:Suppress("MagicNumber")

package hk.edu.hkmu.models.license

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ManifestBody
import hk.edu.hkmu.models.manifest.ModelDescriptor
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.models.manifest.UserProvidedModelRegistrar
import hk.edu.hkmu.models.store.FakeHttpServer
import hk.edu.hkmu.models.store.ModelStore
import hk.edu.hkmu.models.store.ModelStoreConfig
import hk.edu.hkmu.models.store.PartialFiles
import hk.edu.hkmu.models.store.Respond
import hk.edu.hkmu.models.store.TestModelFixtures
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Manual-QA (Tier A) probes for the license policy. Assertions are the real contract; the `println`
 * lines emit the observable values captured into `.omo/evidence/task-25-ondevice-sign-asr-tts.log`.
 */
class ManualQaProbeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val servers = mutableListOf<FakeHttpServer>()
    private val signer = LicenseTestFixtures.signer()
    private val loader = LicenseTestFixtures.loaderFor(signer)

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    @Test
    fun `manual qa self-use admits the nc nllb model with a recorded acknowledgment`() {
        val nllb = vendorDescriptor("nllb", "CC-BY-NC-4.0")
        val decision = DefaultLicensePolicy().evaluate(nllb.license, UsageIntent.SelfUse) as LicenseDecision.Allowed
        val recording = RecordingLoader()
        val registry = registryFor(recording, UsageIntent.SelfUse, nllb)

        val lease = registry.acquire(nllb.id)

        println(
            "QA-SELFUSE model=${nllb.id} kind=${nllb.license.kind} decision=Allowed " +
                "ack=\"${decision.acknowledgment}\""
        )
        println(
            "QA-SELFUSE loaderInvocations=${recording.loaded.size} resident=${registry.isResident(nllb.id)} " +
                "refCount=${registry.refCount(nllb.id)}"
        )
        assertEquals(listOf(nllb.id), recording.loaded)
        assertFalse("SelfUse + NC must record an acknowledgment", decision.acknowledgment.isNullOrBlank())
        lease.close()
    }

    @Test
    fun `manual qa commercial refuses nllb wlasl and byo at the shared registry`() {
        val models = listOf(
            vendorDescriptor("nllb", "CC-BY-NC-4.0"),
            vendorDescriptor("wlasl-tgcn", "C-UDA-1.0"),
            byoDescriptor("csl-byo")
        )
        val recording = RecordingLoader()
        val registry = ModelRegistry(recording, DefaultLicensePolicy(), UsageIntent.Commercial, CAPACITY)
        models.forEach { registry.register(ModelRegistration(it.id, it.license)) }

        models.forEach { model ->
            val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.load(model.id) }
            println(
                "QA-COMMERCIAL model=${model.id} kind=${error.licenseKind} type=${error::class.simpleName} " +
                    "loaderInvocations=${recording.loaded.size}"
            )
        }
        assertEquals("no refused model may reach the loader", 0, recording.loaded.size)
    }

    @Test
    fun `manual qa commercial download of the nc model is blocked before any request`() {
        val body = TestModelFixtures.body()
        val http = server().route("/nllb.onnx", Respond.bytes(body))
        val nllb = signedDescriptor(http, "/nllb.onnx", body, "nllb", "CC-BY-NC-4.0")
        val store = store(temp.newFolder())

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            runBlocking { store.download(nllb, UsageIntent.Commercial).toList() }
        }

        val finalFile = store.installedFile(nllb)
        println(
            "QA-DOWNLOAD model=${nllb.id} kind=${error.licenseKind} requests=${http.requestCount()} " +
                "finalExists=${finalFile.exists()} partExists=${PartialFiles.partFile(finalFile).exists()}"
        )
        assertEquals(0, http.requestCount())
    }

    @Test
    fun `manual qa tampered relabel and host-key signature are refused`() {
        val envelope = signer.envelope(
            ManifestBody(LicenseTestFixtures.RELEASE, listOf(LicenseTestFixtures.entry("nllb", "CC-BY-NC-4.0")))
        )
        val tampered = envelope.copy(payload = envelope.payload.replace("CC-BY-NC-4.0", "Apache-2.0"))
        val tamperedError = assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(signer.json(tampered))
        }

        val hostSigner = LicenseTestFixtures.signer()
        val forged = hostSigner.envelope(
            ManifestBody(LicenseTestFixtures.RELEASE, listOf(LicenseTestFixtures.entry("nllb", "Apache-2.0"))),
            signingKeyPair = hostSigner.keyPair
        )
        val hostError = assertThrows(AiAndroidException.ManifestSignatureInvalid::class.java) {
            loader.load(hostSigner.json(forged))
        }

        println(
            "QA-ADVERSARIAL relabel=${tamperedError::class.simpleName} " +
                "hostkey=${hostError::class.simpleName} (no descriptor produced)"
        )
    }

    private fun registryFor(
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

    private fun byoDescriptor(id: String): ModelDescriptor {
        return UserProvidedModelRegistrar().register(
            UserProvidedModel(
                id = id,
                declaredLicense = "Apache-2.0",
                version = LicenseTestFixtures.VERSION,
                sha256 = LicenseTestFixtures.PLACEHOLDER_SHA,
                abi = setOf(Abi.Any),
                sizeBytes = LicenseTestFixtures.MODEL_SIZE,
                urls = listOf(LicenseTestFixtures.DEFAULT_URL),
                format = ModelFormat.Tflite
            )
        )
    }

    private companion object {
        const val CAPACITY = 4
    }
}
