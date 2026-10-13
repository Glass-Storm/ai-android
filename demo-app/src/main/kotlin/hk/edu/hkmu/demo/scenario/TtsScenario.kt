@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.demo.media.AudioPlayer
import hk.edu.hkmu.tts.SherpaTtsEngine
import kotlinx.coroutines.flow.toList

/** A short Cantonese phrase to synthesize. */
private const val DEMO_TEXT = "你好，呢個係示範"

/** Cantonese VITS native rate (the loaded model remains authoritative). */
private const val CANTONESE_SAMPLE_RATE_HZ = 22_050

/**
 * Scenario: real sherpa-onnx VITS TTS.
 *
 * Synthesizes [DEMO_TEXT] with the real [SherpaTtsEngine] reading
 * `models/tts/vits-cantonese-hf-xiaomaiiwn`, concatenates the emitted [hk.edu.hkmu.core.engine.AudioChunk]s,
 * plays them through [AudioPlayer], and reports the total sample count and model-reported rate.
 */
object TtsScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val engine = SherpaTtsEngine(
            config = TtsConfig(language = Language.Cantonese, sampleRateHz = CANTONESE_SAMPLE_RATE_HZ),
            modelRoot = environment.dir("tts")
        )
        return try {
            val chunks = engine.synthesize(DEMO_TEXT).toList()
            val samples = chunks.flatMap { it.samples.toList() }.toFloatArray()
            val sampleRateHz = chunks.firstOrNull()?.sampleRateHz ?: CANTONESE_SAMPLE_RATE_HZ
            if (samples.isNotEmpty()) {
                AudioPlayer.play(samples, sampleRateHz)
            }
            render(chunks.size, samples.size, sampleRateHz)
        } finally {
            engine.close()
        }
    }

    private fun render(chunkCount: Int, sampleCount: Int, sampleRateHz: Int): String = buildString {
        appendLine("Real engine: hk.edu.hkmu.tts.SherpaTtsEngine (model models/tts/vits-cantonese-hf-xiaomaiiwn)")
        appendLine("Text: \"$DEMO_TEXT\"")
        appendLine("AudioChunks: $chunkCount")
        appendLine("Total samples: $sampleCount")
        append("Sample rate: $sampleRateHz Hz")
    }
}
