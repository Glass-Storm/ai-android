package hk.edu.hkmu.asr

import hk.edu.hkmu.core.config.AsrConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A guard for the committed held-out fixture set.
 *
 * The fixture WAVs are intentionally non-speech (no real models may be fetched here): the real
 * model's device run is Tier B ([SenseVoiceDeviceTierBTest]). This test proves the set is committed,
 * is valid 16 kHz mono 16-bit PCM, and that a non-speech clip yields no segments.
 */
class AsrFixtureSetTest {

    @Test
    fun `held-out fixture set is present and is 16 kHz mono 16-bit PCM`() {
        val entries = manifest()
        assertTrue("manifest should list at least one fixture", entries.isNotEmpty())

        entries.forEach { entry ->
            val wav = WavFixtureReader.read("/fixtures/asr/${entry.fileName}")
            assertEquals("sample rate of ${entry.fileName}", SAMPLE_RATE_HZ, wav.sampleRateHz)
            assertEquals("channels of ${entry.fileName}", MONO, wav.channels)
            assertEquals("bits per sample of ${entry.fileName}", BITS_PER_SAMPLE, wav.bitsPerSample)
            assertTrue("samples present in ${entry.fileName}", wav.samples.isNotEmpty())
        }
    }

    @Test
    fun `silence fixture is documented as an empty transcript and yields no segments`() = runTest {
        val byName = manifest().associateBy { it.fileName }
        assertEquals("", byName.getValue(SILENCE_FIXTURE).expectedText)

        val wav = WavFixtureReader.read("/fixtures/asr/$SILENCE_FIXTURE")
        val engine = SenseVoiceAsrEngine.forTesting(AsrConfig(), FakeSenseVoiceRecognizer(), Dispatchers.Unconfined)

        val segments = engine.transcribe(SyntheticAudio.chunks(wav.samples, CHUNK_SAMPLES).asFlow()).toList()

        assertTrue(segments.isEmpty())
    }

    private fun manifest(): List<FixtureEntry> {
        val text = AsrFixtureSetTest::class.java
            .getResourceAsStream(MANIFEST_PATH)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("missing fixture manifest $MANIFEST_PATH")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split('\t')
                FixtureEntry(fileName = columns[0], expectedText = columns.getOrElse(1) { "" })
            }
            .toList()
    }

    private class FixtureEntry(
        val fileName: String,
        val expectedText: String
    )

    private companion object {
        const val MANIFEST_PATH = "/fixtures/asr/transcripts.tsv"
        const val SILENCE_FIXTURE = "silence_0.5s_16k_mono.wav"
        const val CHUNK_SAMPLES = 1_000
        const val SAMPLE_RATE_HZ = 16_000
        const val MONO = 1
        const val BITS_PER_SAMPLE = 16
    }
}
