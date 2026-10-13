@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.pipeline.PipelineSpec
import hk.edu.hkmu.core.pipeline.SignToTextPipeline
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

/**
 * Scenario 4: the real sign pipeline.
 *
 * [SignToTextPipeline] runs the real `Sign -> Recombination` order declared by
 * [PipelineSpec.SignToText]. The [FakeSignEngine] emits one prediction per scripted window and the
 * [FakePostProcessor] recombines the labels.
 */
object SignScenario {
    /** Runs scripted sign windows through the pipeline and renders the recognized labels. */
    suspend fun run(): String {
        val sign = FakeSignEngine(
            listOf(
                SignPrediction("H", 0.94f, 0L, 300L),
                SignPrediction("I", 0.89f, 300L, 600L)
            )
        )
        val postProcessor = FakePostProcessor(listOf("H", "i"))
        val pipeline = SignToTextPipeline(
            spec = PipelineSpec.SignToText,
            sign = sign,
            postProcessor = postProcessor
        )
        val frames = flowOf(SignFrame(FloatArray(LANDMARK_VECTOR_SIZE) { 0.01f }, 0L))
        val results = pipeline.recognize(frames).toList()

        return buildString {
            appendLine("SignToTextPipeline stages: ${pipeline.stages.joinToString(" -> ")}")
            appendLine("Recognized labels (one per window boundary):")
            results.forEach { result ->
                appendLine("  label=${result.label} confidence=${result.confidence} text=\"${result.text}\"")
            }
            append("Engines: FakeSignEngine + FakePostProcessor (test kit)")
        }
    }

    private const val LANDMARK_VECTOR_SIZE = 63
}
