@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.sign.gesture.GestureRecognizerModelCatalog
import hk.edu.hkmu.sign.gesture.MediaPipeGestureRecognizer
import hk.edu.hkmu.testkit.device.DeviceConformanceSupport
import java.io.File
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §8 on-device performance harness for the per-image MediaPipe gesture path.
 *
 * Measures the real [MediaPipeGestureRecognizer.recognize] on an arm64 device and prints ONE
 * greppable line:
 *
 * ```
 * PERF gesture loadMs=<n> p50Ms=<n> p90Ms=<n> rssDeltaKb=<n> iterations=<n> abi=<arm64-v8a> input=<frames|synthetic>
 * ```
 *
 * - `loadMs` — the cold first `recognize`, which includes the native `.task` model load (the
 *   recognizer loads lazily, so a bare `create` does no native work).
 * - `p50Ms` / `p90Ms` — the steady-state (warm) per-image latency percentiles over `iterations`.
 * - `rssDeltaKb` — resident (total PSS) delta in KB across the run, sampled before `create` and
 *   again after the warm loop while the model is still resident.
 * - `input` — `frames` when real hand-bearing stills were provisioned (see below), else
 *   `synthetic`. A `synthetic` run contains no hand, so MediaPipe short-circuits after palm
 *   detection and the number is a detection-only floor, NOT full hand-gesture latency.
 *
 * It is **device-required**: MediaPipe Tasks Vision ships arm64-v8a only and cannot run on the
 * x86_64 emulator, so the test auto-skips (`Assume`) when the ABI is not arm64-v8a or the
 * `gesture_recognizer.task` bundle is not provisioned. The measurement is self-contained and
 * deterministic (no camera, no clock/random input), so repeated device runs are comparable. It
 * **cannot** be evaluated on the host JVM.
 *
 * For representative per-image gesture latency, provision a fixed set of hand-bearing PNG stills at
 * `MODEL_ROOT/gesture/frames/` (one PNG per sign of interest); they are decoded (sorted by name) and
 * reused every iteration. Without them the harness falls back to synthetic gradient bitmaps and
 * reports the detection-only floor (tagged `input=synthetic`).
 *
 * ```
 * ./gradlew :sign:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=hk.edu.hkmu.sign.GestureRecognizerPerfDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class GestureRecognizerPerfDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measuresPerImageGestureRecognition() {
        val abi = runningAbi()
        assumeTrue(
            "SKIPPED: abi=$abi is not $PRODUCTION_ABI; MediaPipe Tasks Vision ships arm64-v8a only",
            abi == PRODUCTION_ABI
        )

        val modelDir = File(MODEL_ROOT, GESTURE_DIR)
        val bundle = GestureRecognizerModelCatalog.GESTURE_ENTRY.fileName
        val missing = DeviceConformanceSupport.missingProvisioned(modelDir, listOf(bundle))
        assumeTrue(
            "SKIPPED: gesture model not provisioned at $modelDir (missing=$missing)",
            missing.isEmpty()
        )

        val handFrames = provisionedHandFrames(File(modelDir, FRAMES_DIR))
        val bitmaps = handFrames.ifEmpty { fixedInputBitmaps(INPUT_BITMAPS) }
        val inputMode = if (handFrames.isEmpty()) INPUT_SYNTHETIC else INPUT_FRAMES
        if (handFrames.isEmpty()) {
            println(
                "PERF gesture note input=$INPUT_SYNTHETIC is a detection-only floor (no hand); " +
                    "provision $modelDir/$FRAMES_DIR/*.png with a hand for representative numbers"
            )
        }

        val pssBeforeKb = pssKb()

        val recognizer = MediaPipeGestureRecognizer.create(context, modelDir)
        val coldStartNanos = System.nanoTime()
        val coldPrediction = recognizer.recognize(bitmaps.first())
        val loadMs = (System.nanoTime() - coldStartNanos) / NANOS_PER_MILLI

        val warmMillis = ArrayList<Long>(ITERATIONS)
        repeat(ITERATIONS) { index ->
            val bitmap = bitmaps[(index + 1) % bitmaps.size]
            val startNanos = System.nanoTime()
            recognizer.recognize(bitmap)
            warmMillis += (System.nanoTime() - startNanos) / NANOS_PER_MILLI
        }

        val pssAfterKb = pssKb()
        recognizer.close()

        val rssDeltaKb = pssAfterKb - pssBeforeKb
        val sortedWarm = warmMillis.sorted()
        val p50Ms = percentile(sortedWarm, P50)
        val p90Ms = percentile(sortedWarm, P90)

        println(
            "PERF gesture loadMs=$loadMs p50Ms=$p50Ms p90Ms=$p90Ms rssDeltaKb=$rssDeltaKb " +
                "iterations=$ITERATIONS abi=$abi input=$inputMode"
        )
        println(
            "PERF gesture detail input=$inputMode frames=${handFrames.size} warm=$ITERATIONS " +
                "coldMs=$loadMs warmMinMs=${sortedWarm.first()} warmMaxMs=${sortedWarm.last()} " +
                "coldLabel=${coldPrediction?.label ?: "none"}"
        )
        println("PERF gesture memory pssBeforeKb=$pssBeforeKb pssAfterKb=$pssAfterKb rssDeltaKb=$rssDeltaKb")

        assertEquals("warm recognize must yield $ITERATIONS samples", ITERATIONS, warmMillis.size)
        assertTrue("warm recognize latencies must be non-negative", sortedWarm.all { it >= 0L })
    }

    private fun runningAbi(): String =
        Build.SUPPORTED_ABIS.firstOrNull()?.takeIf { name -> name.isNotBlank() } ?: "unknown"

    private fun pssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }

    private fun provisionedHandFrames(framesDir: File): List<Bitmap> {
        val pngs = framesDir
            .listFiles { file -> file.isFile && file.name.endsWith(".png", ignoreCase = true) }
            ?.sortedBy { file -> file.name }
            ?: return emptyList()
        return pngs.mapNotNull { png -> BitmapFactory.decodeFile(png.absolutePath) }
    }

    private fun fixedInputBitmaps(count: Int): List<Bitmap> = List(count) { index -> gradientBitmap(index) }

    private fun gradientBitmap(index: Int): Bitmap {
        val pixels = IntArray(IMAGE_WIDTH * IMAGE_HEIGHT)
        for (y in 0 until IMAGE_HEIGHT) {
            for (x in 0 until IMAGE_WIDTH) {
                val red = (x + index * CHANNEL_STRIDE) % CHANNEL_RANGE
                val green = (y + index * CHANNEL_STRIDE) % CHANNEL_RANGE
                val blue = (x + y) % CHANNEL_RANGE
                pixels[y * IMAGE_WIDTH + x] = Color.rgb(red, green, blue)
            }
        }
        return Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, IMAGE_WIDTH, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
        }
    }

    private fun percentile(sortedMillis: List<Long>, percent: Int): Long {
        if (sortedMillis.isEmpty()) {
            return 0L
        }
        val rank = ceil(percent / PERCENT.toDouble() * sortedMillis.size).toInt()
        return sortedMillis[(rank - 1).coerceIn(0, sortedMillis.lastIndex)]
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/sign"
        const val GESTURE_DIR = "gesture"
        const val FRAMES_DIR = "frames"
        const val INPUT_FRAMES = "frames"
        const val INPUT_SYNTHETIC = "synthetic"
        const val PRODUCTION_ABI = "arm64-v8a"
        const val ITERATIONS = 30
        const val INPUT_BITMAPS = 4
        const val IMAGE_WIDTH = 640
        const val IMAGE_HEIGHT = 480
        const val CHANNEL_RANGE = 256
        const val CHANNEL_STRIDE = 24
        const val P50 = 50
        const val P90 = 90
        const val PERCENT = 100
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
