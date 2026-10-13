@file:Suppress("MagicNumber")

package hk.edu.hkmu.diarize

import androidx.test.ext.junit.runners.AndroidJUnit4
import hk.edu.hkmu.core.config.SpeakerEmbeddingConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.EnrollmentDirectory
import hk.edu.hkmu.testkit.device.DeviceConformanceSupport
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device conformance for the speaker-embedding extractor (mirrors
 * [SherpaDiarizationDeviceConformanceTest]).
 *
 * Runs the **real** sherpa-onnx standalone embedding extractor against a provisioned audio fixture on
 * an arm64 device / x86_64 emulator and verifies a voiceprint comes back and maps to a display name
 * through an [EnrollmentDirectory]. Every assertion auto-skips with a `SKIPPED:` reason when its input
 * is not provisioned, so the suite never fails on a machine without models (and never requires models
 * at build time).
 *
 * Provision:
 * - `MODEL_ROOT/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx`.
 * - `FIXTURE_DIR/<wav>` a ≤30 s, 16 kHz mono 16-bit PCM single-speaker utterance.
 */
@RunWith(AndroidJUnit4::class)
class SherpaSpeakerEmbeddingDeviceConformanceTest {

    @Test
    fun extractsVoiceprintAndResolvesItThroughEnrollment() {
        val missing = DeviceConformanceSupport.missingProvisioned(File(MODEL_ROOT), REQUIRED_MODEL_FILES)
        assumeTrue(
            "SKIPPED: speaker-embedding model not provisioned at $MODEL_ROOT (missing=$missing)",
            missing.isEmpty()
        )
        val wav = File(FIXTURE_DIR, FIXTURE_WAV)
        assumeTrue("SKIPPED: no fixture wav at $wav", wav.isFile)

        val samples = readPcm16Mono(wav)
        val extractor = SherpaSpeakerEmbeddingExtractor.create(
            config = SpeakerEmbeddingConfig(),
            modelDir = File(MODEL_ROOT)
        )
        val voiceprints = try {
            runBlocking { extractor.extract(flowOf(AudioChunk(samples, SAMPLE_RATE_HZ))).toList() }
        } finally {
            extractor.close()
        }
        val voiceprint = voiceprints.singleOrNull()
        println("TIER_B speaker-embedding fixture=$FIXTURE_WAV dim=${voiceprint?.embedding?.size}")
        requireNotNull(voiceprint) { "exactly one voiceprint per utterance" }

        assertTrue("voiceprint must be non-empty", voiceprint.embedding.isNotEmpty())
        assertEquals(SherpaSpeakerEmbeddingExtractor.MODEL_ID, voiceprint.modelId)

        val directory = EnrollmentDirectory().enroll("self", voiceprint)
        val match = directory.match(voiceprint)
        assertTrue("self-match must reach the match threshold", match != null)
        assertEquals("self-match similarity is exactly one", 1f, match?.similarity ?: 0f, TOLERANCE)
    }

    private fun readPcm16Mono(file: File): FloatArray {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(WAV_HEADER_BYTES)
        val count = (bytes.size - WAV_HEADER_BYTES) / Short.SIZE_BYTES
        val samples = FloatArray(count)
        for (index in samples.indices) {
            samples[index] = buffer.short.toFloat() / Short.MAX_VALUE
        }
        return samples
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/sherpa-diarize"
        const val FIXTURE_DIR = "/data/local/tmp/sherpa-diarize/test-wavs"
        const val FIXTURE_WAV = "single-speaker.wav"
        const val SAMPLE_RATE_HZ = 16_000
        const val WAV_HEADER_BYTES = 44
        const val TOLERANCE = 1e-4f
        val REQUIRED_MODEL_FILES: List<String> = listOf(DiarizationModels.EMBEDDING_FILE)
    }
}
