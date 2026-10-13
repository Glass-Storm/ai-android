package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.recombine.LlamaPostProcessor
import kotlinx.coroutines.flow.toList

/** Input the recombination LLM rewrites. */
private const val DEMO_TEXT = "你好 世界 呢個 係 示範"

/**
 * Scenario: real llama.cpp recombination.
 *
 * Runs [DEMO_TEXT] through the real [LlamaPostProcessor] backed by the pinned Qwen2.5-0.5B GGUF at
 * `models/llm/qwen2.5-0.5b-instruct-q4_k_m.gguf`. The native session is created lazily on the first
 * collected token; greedy decoding (the default) makes the output deterministic.
 */
object RecombineScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val modelFile = environment.dir("llm", "qwen2.5-0.5b-instruct-q4_k_m.gguf")
        val processor = LlamaPostProcessor.create(
            config = PostProcessConfig(language = Language.Cantonese),
            modelFile = modelFile
        )
        return try {
            val pieces = processor.process(DEMO_TEXT).toList()
            render(pieces.joinToString(""), pieces.size)
        } finally {
            processor.close()
        }
    }

    private fun render(output: String, pieceCount: Int): String = buildString {
        appendLine("Real engine: hk.edu.hkmu.recombine.LlamaPostProcessor (Qwen2.5-0.5B-Instruct Q4_K_M)")
        appendLine("Prompt: \"$DEMO_TEXT\"")
        appendLine("Streamed pieces: $pieceCount")
        append("Recombined: \"$output\"")
    }
}
