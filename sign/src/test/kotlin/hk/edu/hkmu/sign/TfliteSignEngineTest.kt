@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.sign.model.SignLanguages
import hk.edu.hkmu.sign.model.SignModelCatalog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier A acceptance for the windowed [TfliteSignEngine] over an injected fake classifier.
 *
 * The asl model is resolved through the real registry (catalog + license gate + asset validation) with
 * a fake classifier factory, so the shipped LiteRT runtime is exercised only by the factory's contract,
 * never loaded.
 */
class TfliteSignEngineTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sign-engine").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `fake classifier resolves the asl model and emits a labelled prediction`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(100, 43))
        val registry = registry(UsageIntent.SelfUse, factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val engine = TfliteSignEngine.forTesting(tgcnConfig(stride = 50), registry)

        val predictions = engine.recognize(SignTestModels.frames(50, FRAME_VALUES).asFlow()).toList()

        assertEquals(1, predictions.size)
        val prediction = predictions.single()
        assertEquals("wlasl-43", prediction.label)
        assertTrue(prediction.confidence > 0.99f)
        assertEquals(0L, prediction.windowStartMillis)
        assertEquals(49L, prediction.windowEndMillis)
        assertEquals(1, factory.createCount)

        engine.close()
        registry.close()
        assertTrue(factory.created.single().closed)
    }

    @Test
    fun `non overlapping stride emits one prediction per window`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(28, 0))
        val registry = registry(UsageIntent.SelfUse, factory)
        SignTestModels.prepare(root, registry.requireSpec(FINGERSPELLING))
        val engine = TfliteSignEngine.forTesting(fingerspellingConfig(stride = 30), registry)

        val predictions = engine.recognize(SignTestModels.frames(90, 63).asFlow()).toList()

        assertEquals(3, predictions.size)
        assertEquals(listOf("A", "A", "A"), predictions.map { it.label })
        engine.close()
    }

    @Test
    fun `overlapping stride emits more windows than a disjoint split`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(100, 43))
        val registry = registry(UsageIntent.SelfUse, factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val engine = TfliteSignEngine.forTesting(tgcnConfig(stride = 25), registry)

        val predictions = engine.recognize(SignTestModels.frames(100, FRAME_VALUES).asFlow()).toList()

        assertEquals(3, predictions.size)
        engine.close()
    }

    @Test
    fun `empty input emits nothing and completes`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(100, 43))
        val registry = registry(UsageIntent.SelfUse, factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val engine = TfliteSignEngine.forTesting(tgcnConfig(stride = 50), registry)

        val predictions = engine.recognize(emptyFlow()).toList()

        assertTrue(predictions.isEmpty())
        assertEquals(1, factory.createCount)
        engine.close()
    }

    @Test
    fun `unknown classifier id is model not available at construction`() {
        val registry = registry(UsageIntent.SelfUse, RecordingSignClassifierFactory(FloatArray(0)))

        val exception = assertThrows(AiAndroidException.ModelNotAvailable::class.java) {
            TfliteSignEngine.forTesting(
                SignConfig(classifierId = "csl", language = SignLanguages.Csl),
                registry
            )
        }

        assertEquals("csl", exception.modelId)
    }

    @Test
    fun `commercial intent refuses the shipped asl model at construction`() {
        val registry = registry(UsageIntent.Commercial, RecordingSignClassifierFactory(FloatArray(0)))

        assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            TfliteSignEngine.forTesting(tgcnConfig(stride = 50), registry)
        }
    }

    private fun registry(intent: UsageIntent, factory: RecordingSignClassifierFactory): SignClassifierRegistry {
        return SignClassifierRegistry(
            modelRoot = root,
            catalog = SignModelCatalog.Standard,
            licensePolicy = DefaultLicensePolicy(),
            intent = intent,
            factory = factory,
            capacity = ModelRegistry.DEFAULT_CAPACITY
        )
    }

    private fun tgcnConfig(stride: Int): SignConfig = SignConfig(
        classifierId = TGCN,
        windowSize = 50,
        stride = stride,
        language = SignLanguages.Asl
    )

    private fun fingerspellingConfig(stride: Int): SignConfig = SignConfig(
        classifierId = FINGERSPELLING,
        windowSize = 30,
        stride = stride,
        language = SignLanguages.Asl
    )

    private companion object {
        const val TGCN = "tgcn-wlasl-asl100"
        const val FINGERSPELLING = "signbridge-asl-alphabet"

        /** TGCN frame: 55 keypoints x (x, y). */
        const val FRAME_VALUES = 110
    }
}
