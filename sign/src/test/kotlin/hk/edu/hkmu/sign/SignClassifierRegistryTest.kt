@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.sign.model.SignByoContracts
import hk.edu.hkmu.sign.model.SignModelCatalog
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Tier A acceptance for classifier resolution + the license gate on the shared load path.
 *
 * Every case drives the real [SignClassifierRegistry] over a fake classifier factory and a dummy model
 * file, so the native LiteRT runtime and a real model are never needed.
 */
class SignClassifierRegistryTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sign-registry").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `shipped asl license kinds are recorded correctly`() {
        val (registry, _) = registry(UsageIntent.SelfUse)

        assertEquals(LicenseKind.NonCommercial, registry.specFor(TGCN)?.license?.kind)
        assertEquals(LicenseKind.Permissive, registry.specFor(FINGERSPELLING)?.license?.kind)
    }

    @Test
    fun `csl request with no model is a typed model not available`() {
        val (registry, _) = registry(UsageIntent.SelfUse)

        val exception = assertThrows(AiAndroidException.ModelNotAvailable::class.java) {
            registry.requireSpec("csl")
        }

        assertEquals("csl", exception.modelId)
    }

    @Test
    fun `wlasl derived model is restricted under commercial and never loaded`() {
        val (registry, factory) = registry(UsageIntent.Commercial, SignTestModels.peakLogits(100, 43))

        val exception = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            registry.acquire(TGCN)
        }

        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(UsageIntent.Commercial, exception.intent)
        assertEquals(0, factory.createCount)
    }

    @Test
    fun `byo csl is unknown license and restricted under commercial`() {
        val (registry, factory) = registry(UsageIntent.Commercial, SignTestModels.peakLogits(100, 0))

        val spec = registry.registerByo(cslByo(), SignByoContracts.Csl)

        assertEquals(LicenseKind.UnknownLicense, spec.license.kind)
        assertFalse(spec.shipped)
        assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.acquire("csl-byo") }
        assertEquals(0, factory.createCount)
    }

    @Test
    fun `self use acquires the shipped asl model through the fake classifier`() {
        val (registry, factory) = registry(UsageIntent.SelfUse, SignTestModels.peakLogits(100, 43))
        SignTestModels.prepare(root, registry.requireSpec(TGCN))

        registry.acquire(TGCN).use { lease ->
            assertEquals(TGCN, lease.handle.spec.classifierId)
            assertEquals(100, lease.handle.classify(FloatArray(registry.requireSpec(TGCN).inputElements)).size)
        }
        assertEquals(1, factory.createCount)
    }

    @Test
    fun `self use permits a byo csl model and records its spec`() {
        val (registry, _) = registry(UsageIntent.SelfUse)
        val spec = registry.registerByo(cslByo(), SignByoContracts.Csl)
        SignTestModels.prepare(root, spec)

        assertNotNull(registry.specFor("csl-byo"))
        registry.acquire("csl-byo").use { lease ->
            assertEquals("csl-byo", lease.handle.spec.classifierId)
        }
    }

    @Test
    fun `ensure allowed surfaces the offending license kind`() {
        val (registry, _) = registry(UsageIntent.Commercial)

        val exception = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            registry.ensureAllowed(TGCN)
        }

        assertEquals(LicenseKind.NonCommercial, exception.licenseKind)
        assertEquals(UsageIntent.Commercial, exception.intent)
    }

    @Test
    fun `self use permits the shipped asl classifier`() {
        val (registry, _) = registry(UsageIntent.SelfUse)

        registry.ensureAllowed(TGCN)
        registry.ensureAllowed(FINGERSPELLING)
    }

    private fun registry(
        intent: UsageIntent,
        logits: FloatArray = FloatArray(0)
    ): Pair<SignClassifierRegistry, RecordingSignClassifierFactory> {
        val factory = RecordingSignClassifierFactory(logits)
        val registry = SignClassifierRegistry(
            modelRoot = root,
            catalog = SignModelCatalog.Standard,
            licensePolicy = DefaultLicensePolicy(),
            intent = intent,
            factory = factory,
            capacity = ModelRegistry.DEFAULT_CAPACITY
        )
        return registry to factory
    }

    private fun cslByo(): UserProvidedModel = UserProvidedModel(
        id = "csl-byo",
        declaredLicense = "Apache-2.0",
        version = "1.0.0",
        sha256 = "a".repeat(64),
        abi = setOf(Abi.Any),
        sizeBytes = 1024,
        urls = listOf("https://example.com/csl.tflite"),
        format = ModelFormat.Tflite
    )

    private companion object {
        const val TGCN = "tgcn-wlasl-asl100"
        const val FINGERSPELLING = "signbridge-asl-alphabet"
    }
}
