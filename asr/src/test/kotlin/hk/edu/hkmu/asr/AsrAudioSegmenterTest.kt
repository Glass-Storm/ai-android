@file:Suppress("MagicNumber")

package hk.edu.hkmu.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tier A acceptance for the energy-VAD [AsrAudioSegmenter]. */
class AsrAudioSegmenterTest {

    @Test
    fun `splits two bursts into two ordered utterances`() {
        val segmenter = AsrAudioSegmenter(SyntheticAudio.SAMPLE_RATE_HZ)

        val utterances = segmenter.accept(
            SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS) +
                SyntheticAudio.tone(SPEECH_MS) + SyntheticAudio.silence(PAUSE_MS)
        )

        assertEquals(2, utterances.size)
        assertTrue(utterances[0].endTimeMillis <= utterances[1].startTimeMillis)
        assertTrue(utterances.all { it.samples.isNotEmpty() })
    }

    @Test
    fun `empty audio produces no utterances`() {
        val segmenter = AsrAudioSegmenter(SyntheticAudio.SAMPLE_RATE_HZ)

        assertTrue(segmenter.accept(FloatArray(0)).isEmpty())
        assertTrue(segmenter.flush().isEmpty())
    }

    @Test
    fun `a single-frame blip is discarded as noise`() {
        val segmenter = AsrAudioSegmenter(SyntheticAudio.SAMPLE_RATE_HZ)

        assertTrue(segmenter.accept(SyntheticAudio.tone(BLIP_MS) + SyntheticAudio.silence(PAUSE_MS)).isEmpty())
        assertTrue(segmenter.flush().isEmpty())
    }

    @Test
    fun `max utterance length force-closes without trailing silence`() {
        val segmenter = AsrAudioSegmenter(
            SyntheticAudio.SAMPLE_RATE_HZ,
            AsrSegmenterConfig(maxUtteranceFrames = MAX_FRAMES, silenceFramesToClose = LONG_SILENCE_FRAMES)
        )

        val utterances = segmenter.accept(SyntheticAudio.tone(SPEECH_MS))

        assertEquals(EXPECTED_FORCED_SEGMENTS, utterances.size)
    }

    @Test
    fun `flush emits an in-progress utterance`() {
        val segmenter = AsrAudioSegmenter(SyntheticAudio.SAMPLE_RATE_HZ)

        assertTrue(segmenter.accept(SyntheticAudio.tone(SPEECH_MS)).isEmpty())
        assertEquals(1, segmenter.flush().size)
    }

    private companion object {
        const val SPEECH_MS = 600
        const val PAUSE_MS = 600
        const val BLIP_MS = 30
        const val MAX_FRAMES = 10
        const val LONG_SILENCE_FRAMES = 1_000
        const val EXPECTED_FORCED_SEGMENTS = 2
    }
}
