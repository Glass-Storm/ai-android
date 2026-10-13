@file:Suppress("MagicNumber")

package hk.edu.hkmu.diarize

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.core.config.DiarizationConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.DiarizationSegment
import hk.edu.hkmu.testkit.device.DeviceConformanceSupport
import hk.edu.hkmu.testkit.device.NativeLibraryAlignment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device conformance for the diarization engine (mirrors [hk.edu.hkmu.asr.SenseVoiceDeviceConformanceTest]).
 *
 * Runs the **real** sherpa-onnx offline diarizer against a provisioned audio fixture on an arm64
 * device / x86_64 emulator and verifies the packaged natives are 16 KB page aligned. Every assertion
 * auto-skips with a `SKIPPED:` reason when its input is not provisioned, so the suite never fails on
 * a machine without models (and never requires models at build time).
 *
 * Provision:
 * - `MODEL_ROOT/{sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx, 3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx}`.
 * - `FIXTURE_DIR/<wav>` a ≤30 s, 16 kHz mono 16-bit PCM multi-speaker utterance.
 */
@RunWith(AndroidJUnit4::class)
class SherpaDiarizationDeviceConformanceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    @Test
    fun packagedNativeLibrariesAre16KbAligned() {
        val report = scanInstalledNatives()
        report.forEach { library ->
            println(
                "TIER_B 16kb ${library.entry} abi=${library.abi} " +
                    "aligned=${library.all16KbAligned} min=${library.minPageAlignHex}"
            )
        }
        assumeTrue("SKIPPED: no packaged native libraries in the installed APKs", report.isNotEmpty())
        val misaligned = report.filterNot { library -> library.all16KbAligned }
        assertTrue("16 KB-misaligned native libraries: $misaligned", misaligned.isEmpty())
    }

    @Test
    fun diarizesProvisionedFixtureWithConsistentLabelsAndIds() {
        val missing = DeviceConformanceSupport.missingProvisioned(File(MODEL_ROOT), REQUIRED_MODEL_FILES)
        assumeTrue("SKIPPED: diarization model not provisioned at $MODEL_ROOT (missing=$missing)", missing.isEmpty())
        val wav = File(FIXTURE_DIR, FIXTURE_WAV)
        assumeTrue("SKIPPED: no fixture wav at $wav", wav.isFile)

        val samples = readPcm16Mono(wav)
        val engine = SherpaDiarizationEngine.create(DiarizationConfig(), File(MODEL_ROOT))
        val turns = try {
            runBlocking { engine.diarize(flowOf(AudioChunk(samples, SAMPLE_RATE_HZ))).toList() }
        } finally {
            engine.close()
        }
        println("TIER_B diarize fixture=$FIXTURE_WAV turns=${turns.size} labels=${turns.map { it.speakerLabel }}")

        turns.forEach { turn -> assertSpeakerInvariants(turn) }
        assertTrue("turn spans must be non-overlapping and ordered", isOrdered(turns))
    }

    private fun assertSpeakerInvariants(turn: DiarizationSegment) {
        val id = requireNotNull(turn.speakerId) { "every diarized turn must carry a 0-based speakerId" }
        assertTrue("speakerId must be 0-based and non-negative", id >= 0)
        assertTrue("turn start must precede its end", turn.startTimeMillis <= turn.endTimeMillis)
        assertTrue(
            "speakerLabel must be the 1-based form of speakerId",
            turn.speakerLabel == "Speaker ${id + 1}"
        )
    }

    private fun isOrdered(turns: List<DiarizationSegment>): Boolean =
        turns.zipWithNext().all { (current, next) -> current.endTimeMillis <= next.startTimeMillis }

    private fun scanInstalledNatives(): List<NativeLibraryAlignment> {
        val apks = listOf(
            instrumentation.context.applicationInfo.sourceDir,
            context.applicationInfo.sourceDir
        ).distinct()
        return apks.flatMap { path -> DeviceConformanceSupport.scanNativeLibraries(path) }
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
        const val FIXTURE_WAV = "multi-speaker.wav"
        const val SAMPLE_RATE_HZ = 16_000
        const val WAV_HEADER_BYTES = 44
        val REQUIRED_MODEL_FILES: List<String> = listOf(
            "${DiarizationModels.PYANNOTE_SEGMENTATION_DIRECTORY}/${DiarizationModels.PYANNOTE_SEGMENTATION_FILE}",
            DiarizationModels.EMBEDDING_FILE
        )
    }
}
