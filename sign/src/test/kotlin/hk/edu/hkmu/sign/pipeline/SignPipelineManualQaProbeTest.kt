@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.pipeline

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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the human-readable sign→text observables the task requires — the
 * ordered labels/text of a fake pipeline run, and the adversarial low-confidence ⇒
 * `SignResult.lowConfidence` with **no** recombination attempted.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :sign:testDebugUnitTest --tests '*SignPipelineManualQaProbeTest' -i --rerun-tasks`.
 */
class SignPipelineManualQaProbeTest {

    @Test
    fun `manual QA happy - ordered labels and recombined text`() = runTest {
        val root = Files.createTempDirectory("sign-pipeline-manualqa").toFile()
        val factory = RecordingSignClassifierFactory(SignTestModels.peakLogits(CLASSES, GLOSS_INDEX))
        val registry = registry(root, factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val postProcessor = RecordingPostProcessor()
        val pipeline = SignPipeline.create(
            extractor = FakeLandmarkExtractor(),
            registry = registry,
            config = SignPipelineConfig(classifierId = TGCN, stride = WINDOW),
            postProcessor = postProcessor,
            dispatcher = Dispatchers.Unconfined
        )

        val results = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.movingStream(FRAMES, KEYPOINTS))
        ).toList()

        val windows = results.map { "[${it.windowStartMillis},${it.windowEndMillis}]ms" }

        println("SIGN-PIPELINE-MANUAL-QA happy stages=extract>segment>normalize>classify>recombine")
        println("SIGN-PIPELINE-MANUAL-QA happy ordered labels=${results.map { it.label }}")
        println("SIGN-PIPELINE-MANUAL-QA happy ordered text=${results.map { it.text }}")
        println("SIGN-PIPELINE-MANUAL-QA happy windows=$windows")
        println("SIGN-PIPELINE-MANUAL-QA happy recombined=${results.all { it.recombined }}")

        assertTrue(results.size > 1)
        assertTrue(results.all { !it.lowConfidence })
        pipeline.close()
        root.deleteRecursively()
    }

    @Test
    fun `manual QA adversarial - low confidence flags and skips recombination`() = runTest {
        val root = Files.createTempDirectory("sign-pipeline-manualqa-adversarial").toFile()
        val factory = RecordingSignClassifierFactory(FloatArray(CLASSES))
        val registry = registry(root, factory)
        SignTestModels.prepare(root, registry.requireSpec(TGCN))
        val postProcessor = RecordingPostProcessor()
        val pipeline = SignPipeline.create(
            extractor = FakeLandmarkExtractor(),
            registry = registry,
            config = SignPipelineConfig(classifierId = TGCN, stride = WINDOW),
            postProcessor = postProcessor,
            dispatcher = Dispatchers.Unconfined
        )

        val result = pipeline.recognizeLandmarks(
            PipelineTestFixtures.streamOf(PipelineTestFixtures.movingStream(WINDOW, KEYPOINTS))
        ).toList().single()

        println(
            "SIGN-PIPELINE-MANUAL-QA adversarial lowConfidence=${result.lowConfidence} " +
                "confidence=${result.confidence} recombined=${result.recombined} " +
                "recombinationAttempts=${postProcessor.inputs.size}"
        )

        assertTrue(result.lowConfidence)
        assertTrue(postProcessor.inputs.isEmpty())
        pipeline.close()
        root.deleteRecursively()
    }

    private fun registry(root: File, factory: RecordingSignClassifierFactory): SignClassifierRegistry =
        SignClassifierRegistry(
            modelRoot = root,
            catalog = SignModelCatalog.Standard,
            licensePolicy = DefaultLicensePolicy(),
            intent = UsageIntent.SelfUse,
            factory = factory,
            capacity = ModelRegistry.DEFAULT_CAPACITY
        )

    private companion object {
        const val TGCN = "tgcn-wlasl-asl100"
        const val CLASSES = 100
        const val KEYPOINTS = 55
        const val GLOSS_INDEX = 43
        const val WINDOW = 50
        const val FRAMES = 150
    }
}
