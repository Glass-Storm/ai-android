@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.testkit.device.DeviceConformanceSupport
import hk.edu.hkmu.testkit.device.NativeLibraryAlignment
import hk.edu.hkmu.tts.model.TtsModelCatalog
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device conformance for the TTS engine (todo 28).
 *
 * Runs the **real** sherpa-onnx VITS engine for every shipped voice on an arm64 device / x86_64
 * emulator, asserts the audio is non-empty at the model-reported rate, and keeps the real-time factor
 * within the Budgets table (RTF ≤ 0.5). It also verifies the packaged natives are 16 KB page aligned.
 * Each assertion auto-skips with a `SKIPPED:` reason when its voice is not provisioned.
 *
 * Provision: `MODEL_ROOT/<directoryName>/…` per [TtsModelCatalog] (Cantonese xiaomaiiwn, Mandarin
 * VITS, English Piper).
 */
@RunWith(AndroidJUnit4::class)
class SherpaTtsDeviceConformanceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    private val modelRoot = File(MODEL_ROOT)

    private val sentences: Map<Language, String> = mapOf(
        Language.Cantonese to "你好，今日天氣好好。",
        Language.Mandarin to "你好，今天天气很好。",
        Language.English to "Hello, the weather is nice today."
    )

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
    fun synthesizesEveryShippedVoiceWithinRealTimeBudget() {
        assumeTrue("SKIPPED: no device models at $modelRoot", modelRoot.isDirectory)
        for ((language, sentence) in sentences) {
            val spec = TtsModelCatalog.Standard.requireLanguage(language)
            val voiceDir = File(modelRoot, spec.directoryName)
            assumeTrue("SKIPPED: model '${spec.id}' not provisioned", voiceDir.isDirectory)

            val engine = SherpaTtsEngine(TtsConfig(language = language), modelRoot)
            val startedAt = System.nanoTime()
            val chunks = try {
                runBlocking { engine.synthesizeInt16(sentence).toList() }
            } finally {
                engine.close()
            }
            val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
            val totalSamples = chunks.sumOf { chunk -> chunk.samples.size }
            val rate = chunks.firstOrNull()?.sampleRateHz ?: 0
            val audioMillis = if (rate > 0) totalSamples * MILLIS_PER_SECOND / rate.toDouble() else 0.0
            val realTimeFactor = if (audioMillis > 0) elapsedMs / audioMillis else Double.MAX_VALUE
            println(
                "TIER_B tts lang=${language.tag} samples=$totalSamples rate=$rate " +
                    "elapsedMs=$elapsedMs rtf=$realTimeFactor"
            )
            assertTrue("$language produced no samples", totalSamples > 0)
            assertTrue("$language reported a non-positive rate", rate > 0)
            assertTrue(
                "TTS real-time factor $realTimeFactor exceeds budget $TTS_RTF_BUDGET",
                realTimeFactor <= TTS_RTF_BUDGET
            )
        }
    }

    private fun scanInstalledNatives(): List<NativeLibraryAlignment> {
        val apks = listOf(
            instrumentation.context.applicationInfo.sourceDir,
            context.applicationInfo.sourceDir
        ).distinct()
        return apks.flatMap { path -> DeviceConformanceSupport.scanNativeLibraries(path) }
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/sherpa-tts"
        const val TTS_RTF_BUDGET = 0.5
        const val MILLIS_PER_SECOND = 1000.0
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
