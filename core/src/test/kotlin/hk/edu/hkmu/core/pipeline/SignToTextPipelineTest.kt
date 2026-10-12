@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A: the sign composition runs `SLR → recombination` over the core engine interfaces. The fake
 * post-processor records its input, proving recombination ran after the sign source and was skipped
 * when opted out.
 */
class SignToTextPipelineTest {

    @Test
    fun `SLR then recombination runs in the configured order`() = runTest {
        val sign = FakeSignEngine(listOf(prediction("wlasl-43")))
        val postProcessor = FakePostProcessor(listOf("wlasl", "-43"))

        val pipeline = SignToTextPipeline(PipelineSpec.SignToText, sign, postProcessor)

        val result = pipeline.recognize(emptyFlow()).toList().single()

        assertEquals("wlasl-43", postProcessor.lastInput)
        assertEquals("wlasl-43", result.label)
        assertEquals("wlasl-43", result.recombinedText)
        assertEquals("wlasl-43", result.text)
        assertEquals(0.97f, result.confidence, 1e-6f)
        assertEquals(1, sign.emittedPredictions.size)
    }

    @Test
    fun `recombination opt-out keeps the raw label`() = runTest {
        val sign = FakeSignEngine(listOf(prediction("A")))
        val postProcessor = FakePostProcessor(listOf("A!"))

        val pipeline = SignToTextPipeline(PipelineSpec.SignToText, sign, postProcessor, recombinationEnabled = false)

        val result = pipeline.recognize(emptyFlow()).toList().single()

        assertNull(postProcessor.lastInput)
        assertNull(result.recombinedText)
        assertEquals("A", result.text)
        assertEquals(listOf(PipelineStage.Sign), pipeline.effectiveStages)
    }

    @Test
    fun `declaring recombination without a post-processor fails fast`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SignToTextPipeline(PipelineSpec.SignToText, FakeSignEngine())
        }

        assertTrue(failure.reason.contains("PostProcessor"))
    }

    @Test
    fun `a speech spec cannot drive the sign pipeline`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            SignToTextPipeline(spec = PipelineSpec.SpeechToText, sign = FakeSignEngine())
        }

        assertTrue(failure.reason.contains("SignToText"))
    }

    private fun prediction(label: String): SignPrediction =
        SignPrediction(label = label, confidence = 0.97f, windowStartMillis = 0, windowEndMillis = 40)
}
