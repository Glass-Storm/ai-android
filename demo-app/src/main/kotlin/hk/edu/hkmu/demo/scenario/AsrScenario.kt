package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.asr.SenseVoiceAsrEngine
import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.demo.media.WavReader
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

/**
 * Scenario: real SenseVoice ASR over the bundled Cantonese clip.
 *
 * Parses `models/assets/demo_yue.wav` (16 kHz mono 16-bit PCM) into one normalized [AudioChunk] and
 * transcribes it with the real [SenseVoiceAsrEngine] reading `models/asr`. The native recognizer is
 * created lazily on the first collected segment, so construction alone loads nothing.
 */
object AsrScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val audioFile = environment.dir("assets", "demo_yue.wav")
        if (!audioFile.isFile) {
            return "bundled audio missing: ${audioFile.path} (provision it from assets/audio/demo_yue.wav)"
        }
        val audio = WavReader.readMono(audioFile)
        val engine = SenseVoiceAsrEngine.create(
            config = AsrConfig(language = Language.Auto),
            modelDir = environment.dir("asr")
        )
        return try {
            val chunk = AudioChunk(audio.samples, audio.sampleRateHz)
            render(audio, engine.transcribe(flowOf(chunk)).toList())
        } finally {
            engine.close()
        }
    }

    private fun render(audio: WavReader.MonoAudio, segments: List<TranscriptionSegment>): String = buildString {
        appendLine("Real engine: hk.edu.hkmu.asr.SenseVoiceAsrEngine (model dir models/asr)")
        appendLine("Input: ${audio.samples.size} samples @ ${audio.sampleRateHz} Hz, single chunk")
        if (segments.isEmpty()) {
            append("No speech detected (empty segment list).")
        } else {
            segments.forEach { segment ->
                appendLine("  text=\"${segment.text}\" isFinal=${segment.isFinal} lang=${segment.language?.tag}")
                appendLine("  span=${segment.startTimeMillis}..${segment.endTimeMillis} ms")
            }
        }
    }
}
