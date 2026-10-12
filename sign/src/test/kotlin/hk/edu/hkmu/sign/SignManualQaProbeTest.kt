@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.sign.model.SignByoContracts
import hk.edu.hkmu.sign.model.SignLanguages
import hk.edu.hkmu.sign.model.SignModelCatalog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Manual-QA (A) probe. Prints the human-readable observables the task requires — the fake-classifier
 * label, the recorded license kind and the license-gate observables — for the evidence log. Run with
 * `-i` to capture `STANDARD_OUT`.
 */
class SignManualQaProbeTest {

    @Test
    fun `manual qa happy path resolves the asl model and prints the label`() = runTest {
        val root = Files.createTempDirectory("sign-manualqa").toFile()
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(100, 43))
        val registry = registry(root, UsageIntent.SelfUse, factory)
        val tgcn = registry.requireSpec("tgcn-wlasl-asl100")
        println("SIGN-MANUAL-QA asl-model=${tgcn.classifierId} license=${tgcn.license.kind}")
        println("SIGN-MANUAL-QA asl-model-spdx=${tgcn.license.spdxId}")
        println("SIGN-MANUAL-QA fingerspelling license=${registry.specFor("signbridge-asl-alphabet")?.license?.kind}")
        SignTestModels.prepare(root, tgcn)

        val engine = TfliteSignEngine.forTesting(
            SignConfig(classifierId = tgcn.classifierId, stride = 50, language = SignLanguages.Asl),
            registry
        )
        val prediction = engine.recognize(SignTestModels.frames(50, 110).asFlow()).toList().single()
        println(
            "SIGN-MANUAL-QA happy label=${prediction.label} confidence=${prediction.confidence} " +
                "window=[${prediction.windowStartMillis},${prediction.windowEndMillis}]ms"
        )

        assertEquals("wlasl-43", prediction.label)
        engine.close()
        root.deleteRecursively()
    }

    @Test
    fun `manual qa adversarial model not available and license restricted`() {
        val root = Files.createTempDirectory("sign-manualqa-adversarial").toFile()
        val commercial = registry(root, UsageIntent.Commercial, RecordingSignClassifierFactory(FloatArray(0)))

        val missing = assertThrows(AiAndroidException.ModelNotAvailable::class.java) {
            TfliteSignEngine.forTesting(
                SignConfig(classifierId = "csl", language = SignLanguages.Csl),
                commercial
            )
        }
        println("SIGN-MANUAL-QA adversarial ModelNotAvailable model=${missing.modelId}")

        val wlasl = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            commercial.ensureAllowed("tgcn-wlasl-asl100")
        }
        println("SIGN-MANUAL-QA adversarial LicenseRestricted model=${wlasl.modelId} license=${wlasl.licenseKind}")

        commercial.registerByo(
            UserProvidedModel(
                id = "csl-byo",
                declaredLicense = "Apache-2.0",
                version = "1.0.0",
                sha256 = "a".repeat(64),
                abi = setOf(Abi.Any),
                sizeBytes = 1024,
                urls = listOf("https://example.com/csl.tflite"),
                format = ModelFormat.Tflite
            ),
            SignByoContracts.Csl
        )
        val byo = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            commercial.ensureAllowed("csl-byo")
        }
        println("SIGN-MANUAL-QA adversarial LicenseRestricted byo model=${byo.modelId} license=${byo.licenseKind}")

        assertEquals(UsageIntent.Commercial, wlasl.intent)
        root.deleteRecursively()
    }

    private fun registry(
        root: File,
        intent: UsageIntent,
        factory: RecordingSignClassifierFactory
    ): SignClassifierRegistry = SignClassifierRegistry(
        modelRoot = root,
        catalog = SignModelCatalog.Standard,
        licensePolicy = DefaultLicensePolicy(),
        intent = intent,
        factory = factory,
        capacity = ModelRegistry.DEFAULT_CAPACITY
    )
}
