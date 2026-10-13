@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.sign

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the §7 hub contract: a headless JVM test can run the full segmentation → normalization →
 * classification path through the **public** `SignPipeline.recognizeLandmarks` seam using only public
 * `:sign` API plus this module's fakes — no device, no LiteRT native, no real model.
 *
 * The pipeline is built by [SignPipelineHarness] over a [FakeSignClassifier]; the streams come from
 * [movingFrames]/[stillFrames].
 */
class SignPipelineHarnessTest {

    @Test
    fun `moving synthetic landmarks yield the fake classifier's label with a sane confidence`() = runTest {
        val classifier = FakeSignClassifier(CLASSES, GLOSS_INDEX, CONFIDENCE)
        val harness = SignPipelineHarness.create(classifier)

        val results = harness.use {
            it.pipeline.recognizeLandmarks(landmarkStream(movingFrames(frames = FRAMES))).toList()
        }

        assertTrue("a moving stream must emit at least one SignResult", results.isNotEmpty())
        assertTrue("every result must carry the fake's winning label", results.all { it.label == LABEL })
        assertTrue("the winning confidence must be decisive", results.all { it.confidence > SANE_CONFIDENCE_FLOOR })
        assertEquals(CONFIDENCE, results.first().confidence, CONFIDENCE_TOLERANCE)
        assertTrue("the fake classifier must have been invoked", classifier.inputs.isNotEmpty())
    }

    @Test
    fun `still synthetic landmarks produce no SignResult`() = runTest {
        val classifier = FakeSignClassifier(CLASSES, GLOSS_INDEX, CONFIDENCE)
        val harness = SignPipelineHarness.create(classifier)

        val results = harness.use {
            it.pipeline.recognizeLandmarks(landmarkStream(stillFrames(frames = FRAMES))).toList()
        }

        assertTrue("a motionless stream must emit nothing", results.isEmpty())
        assertTrue("an idle stream must not invoke the classifier", classifier.inputs.isEmpty())
    }

    private companion object {
        const val CLASSES = 100
        const val GLOSS_INDEX = 43
        const val LABEL = "wlasl-43"
        const val CONFIDENCE = 0.9f
        const val CONFIDENCE_TOLERANCE = 0.05f
        const val SANE_CONFIDENCE_FLOOR = 0.5f
        const val FRAMES = 150
    }
}
