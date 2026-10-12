@file:Suppress("MagicNumber")

package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.AsrConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the observable behaviour for the evidence log.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :asr:testDebugUnitTest --tests '*AsrManualQaProbeTest' -i --rerun-tasks`.
 */
class AsrManualQaProbeTest {

    @Test
    fun `manual QA - fake path emits ordered segments and completes`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(
            texts = listOf("呢几个字都表达唔到我想讲嘅意思", "你好", "今日天气好好"),
            detectedTags = listOf("yue", "zh", "zh")
        )
        val engine = SenseVoiceAsrEngine.forTesting(AsrConfig(), recognizer, Dispatchers.Unconfined)

        val segments = engine.transcribe(
            SyntheticAudio.chunks(
                SyntheticAudio.tone(600) + SyntheticAudio.silence(600) +
                    SyntheticAudio.tone(600) + SyntheticAudio.silence(600) +
                    SyntheticAudio.tone(600) + SyntheticAudio.silence(600),
                chunkSize = 1_000
            ).asFlow()
        ).toList()

        println("ASR-MANUAL-QA happy: ${segments.size} ordered final segments")
        segments.forEach { segment ->
            println(
                "ASR-MANUAL-QA   text=${segment.text} start=${segment.startTimeMillis}ms " +
                    "end=${segment.endTimeMillis}ms final=${segment.isFinal} lang=${segment.language?.tag}"
            )
        }
        println("ASR-MANUAL-QA recognizer_calls=${recognizer.recognizedSampleCounts}")

        assertEquals(3, segments.size)
        assertTrue(segments.all { it.isFinal })
    }

    @Test
    fun `manual QA - empty audio adversarial probe yields empty result without crash`() = runTest {
        val recognizer = FakeSenseVoiceRecognizer(texts = listOf("never-emitted"))
        val engine = SenseVoiceAsrEngine.forTesting(AsrConfig(), recognizer, Dispatchers.Unconfined)

        val segments = engine.transcribe(emptyFlow()).toList()

        println(
            "ASR-MANUAL-QA adversarial empty-audio: segments=${segments.size} " +
                "recognizer_calls=${recognizer.recognizedSampleCounts.size} crashed=false"
        )

        assertTrue(segments.isEmpty())
        assertTrue(recognizer.recognizedSampleCounts.isEmpty())
    }
}
