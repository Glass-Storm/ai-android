@file:Suppress("MagicNumber")

package hk.edu.hkmu.asr

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AudioChunk
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
 * Tier B device conformance for the ASR engine (todo 28).
 *
 * Runs the **real** sherpa-onnx SenseVoice engine against a provisioned audio fixture on an arm64
 * device / x86_64 emulator and verifies the packaged natives are 16 KB page aligned. Every assertion
 * auto-skips with a `SKIPPED:` reason when its input is not provisioned, so the suite never fails on
 * a machine without models (and never runs at all without an attached device, per the root guard).
 *
 * Provision:
 * - `MODEL_ROOT/<modelId>/{model.int8.onnx, tokens.txt}` (sherpa-onnx SenseVoice int8).
 * - `FIXTURE_DIR/<wav>` a ≤5 s, 16 kHz mono 16-bit PCM Cantonese utterance.
 */
@RunWith(AndroidJUnit4::class)
class SenseVoiceDeviceConformanceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    private val modelDir = File(MODEL_ROOT, SenseVoiceAsrEngine.MODEL_ID)

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
    fun transcribesProvisionedFixture() {
        val required = listOf("tokens.txt", "model.int8.onnx")
        val missing = DeviceConformanceSupport.missingProvisioned(modelDir, required)
        assumeTrue("SKIPPED: ASR model not provisioned at $modelDir (missing=$missing)", missing.isEmpty())
        val wav = File(FIXTURE_DIR, FIXTURE_WAV)
        assumeTrue("SKIPPED: no fixture wav at $wav", wav.isFile)

        val samples = readPcm16Mono(wav)
        val engine = SenseVoiceAsrEngine.create(AsrConfig(language = FIXTURE_LANGUAGE), modelDir)
        val startedAt = System.nanoTime()
        val segments = try {
            runBlocking { engine.transcribe(flowOf(AudioChunk(samples, SAMPLE_RATE_HZ))).toList() }
        } finally {
            engine.close()
        }
        val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
        val texts = segments.map { segment -> segment.text }
        println("TIER_B asr fixture=$FIXTURE_WAV segments=${segments.size} elapsedMs=$elapsedMs text=$texts")
        assertTrue("no transcription segments produced", segments.isNotEmpty())
        assertTrue("empty transcript", segments.any { segment -> segment.text.isNotBlank() })
        assertTrue(
            "ASR segment latency ${elapsedMs}ms exceeds budget ${ASR_SEGMENT_BUDGET_MS}ms",
            elapsedMs <= ASR_SEGMENT_BUDGET_MS
        )
    }

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
        const val MODEL_ROOT = "/data/local/tmp/sherpa-asr"
        const val FIXTURE_DIR = "/data/local/tmp/sherpa-asr/test-wavs"
        const val FIXTURE_WAV = "yue-1.wav"
        const val SAMPLE_RATE_HZ = 16_000
        const val WAV_HEADER_BYTES = 44
        const val ASR_SEGMENT_BUDGET_MS = 1500L
        const val NANOS_PER_MILLI = 1_000_000L
        val FIXTURE_LANGUAGE: Language = Language.Cantonese
    }
}
