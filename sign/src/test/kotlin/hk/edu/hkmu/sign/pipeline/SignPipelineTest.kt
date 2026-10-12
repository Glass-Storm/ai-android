@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.sign.RecordingSignClassifierFactory
import hk.edu.hkmu.sign.SignClassifierRegistry
import hk.edu.hkmu.sign.SignTestModels
import hk.edu.hkmu.sign.model.SignModelCatalog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier A acceptance for the composed [SignPipeline].
 *
 * A fake classifier is injected through the real [SignClassifierRegistry] (catalog + license gate +
 * asset validation), so the LiteRT runtime is never loaded. Extraction is faked at the landmark seam
 * because MediaPipe is device-only, mirroring how `sign.landmark` itself is tested.
 */
class SignPipelineTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sign-pipeline").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `fake pipeline emits ordered results recombining each confident window`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(TGCN_CLASSES, GLOSS_INDEX))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val postProcessor = RecordingPostProcessor()
        val pipeline = pipeline(registry, postProcessor = postProcessor)

        val results = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.movingStream(FRAMES, TGCN_KEYPOINTS))
        ).toList()

        assertEquals(3, results.size)
        assertEquals(listOf(0, 1, 2), results.map { it.windowIndex })
        assertEquals(listOf(GLOSS, GLOSS, GLOSS), results.map { it.label })
        assertTrue(results.all { !it.lowConfidence && it.recombined })
        assertEquals(List(results.size) { "[$GLOSS]" }, results.map { it.text })
        assertEquals(0L, results.first().windowStartMillis)
        assertEquals(FIRST_WINDOW_END_MILLIS, results.first().windowEndMillis)
        assertEquals(listOf(GLOSS, GLOSS, GLOSS), postProcessor.inputs)

        pipeline.close()
        registry.close()
    }

    @Test
    fun `low confidence flags the result and never attempts recombination`() = runTest {
        val factory = RecordingSignClassifierFactory(FloatArray(TGCN_CLASSES))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val postProcessor = RecordingPostProcessor()
        val pipeline = pipeline(registry, postProcessor = postProcessor)

        val result = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.movingStream(WINDOW, TGCN_KEYPOINTS))
        ).toList().single()

        assertTrue(result.lowConfidence)
        assertFalse(result.recombined)
        assertEquals(result.label, result.text)
        assertTrue("recombination must not run for low confidence", postProcessor.inputs.isEmpty())

        pipeline.close()
        registry.close()
    }

    @Test
    fun `recombination is skipped when disabled even for a confident window`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(TGCN_CLASSES, GLOSS_INDEX))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val postProcessor = RecordingPostProcessor()
        val pipeline = pipeline(registry, postProcessor = postProcessor, recombinationEnabled = false)

        val result = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.movingStream(WINDOW, TGCN_KEYPOINTS))
        ).toList().single()

        assertFalse(result.recombined)
        assertEquals(GLOSS, result.text)
        assertTrue(postProcessor.inputs.isEmpty())

        pipeline.close()
    }

    @Test
    fun `a motionless stream emits no results and loads no classifier`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(TGCN_CLASSES, GLOSS_INDEX))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val pipeline = pipeline(registry)

        val results = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.stillStream(FRAMES, TGCN_KEYPOINTS))
        ).toList()

        assertTrue(results.isEmpty())
        assertEquals(0, factory.createCount)

        pipeline.close()
    }

    @Test
    fun `empty stream emits nothing and loads no classifier`() = runTest {
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(TGCN_CLASSES, GLOSS_INDEX))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val pipeline = pipeline(registry)

        assertTrue(pipeline.recognizeLandmarks(emptyFlow()).toList().isEmpty())
        assertEquals(0, factory.createCount)

        pipeline.close()
    }

    @Test
    fun `unknown classifier id fails fast at construction`() {
        val registry = registry(RecordingSignClassifierFactory(FloatArray(0)))

        val exception = assertThrows(AiAndroidException.ModelNotAvailable::class.java) {
            SignPipeline.create(
                extractor = FakeLandmarkExtractor(),
                registry = registry,
                config = SignPipelineConfig(classifierId = "csl"),
                dispatcher = Dispatchers.Unconfined
            )
        }

        assertEquals("csl", exception.modelId)
    }

    @Test
    fun `commercial intent refuses the non-commercial classifier at construction`() {
        val registry = registry(
            RecordingSignClassifierFactory(FloatArray(TGCN_CLASSES)),
            intent = UsageIntent.Commercial
        )

        assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            SignPipeline.create(
                extractor = FakeLandmarkExtractor(),
                registry = registry,
                config = SignPipelineConfig(classifierId = TGCN),
                dispatcher = Dispatchers.Unconfined
            )
        }
    }

    @Test
    fun `a classifier whose input contract the 2D extractor cannot satisfy is rejected`() {
        val factory = RecordingSignClassifierFactory(FloatArray(FINGERSPELLING_CLASSES))
        val registry = registry(factory)
        SignTestModels.prepare(root, registry.requireSpec(FINGERSPELLING))

        val exception = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SignPipeline.create(
                extractor = FakeLandmarkExtractor(),
                registry = registry,
                config = SignPipelineConfig(classifierId = FINGERSPELLING),
                dispatcher = Dispatchers.Unconfined
            )
        }

        assertTrue(exception.reason.contains("FrameFeatures"))
    }

    private fun pipeline(
        registry: SignClassifierRegistry,
        postProcessor: RecordingPostProcessor? = null,
        recombinationEnabled: Boolean = true
    ): SignPipeline = SignPipeline.create(
        extractor = FakeLandmarkExtractor(),
        registry = registry,
        config = SignPipelineConfig(
            classifierId = TGCN,
            stride = WINDOW,
            recombinationEnabled = recombinationEnabled
        ),
        postProcessor = postProcessor,
        dispatcher = Dispatchers.Unconfined
    )

    private fun registry(
        factory: RecordingSignClassifierFactory,
        intent: UsageIntent = UsageIntent.SelfUse
    ): SignClassifierRegistry = SignClassifierRegistry(
        modelRoot = root,
        catalog = SignModelCatalog.Standard,
        licensePolicy = DefaultLicensePolicy(),
        intent = intent,
        factory = factory,
        capacity = ModelRegistry.DEFAULT_CAPACITY
    )

    private companion object {
        const val TGCN = "tgcn-wlasl-asl100"
        const val FINGERSPELLING = "signbridge-asl-alphabet"
        const val TGCN_CLASSES = 100
        const val FINGERSPELLING_CLASSES = 28
        const val TGCN_KEYPOINTS = 55
        const val GLOSS_INDEX = 43
        const val GLOSS = "wlasl-43"
        const val WINDOW = 50
        const val FRAMES = 150
        const val FIRST_WINDOW_END_MILLIS = 49L * 33L
    }
}
