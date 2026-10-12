@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.sign.landmark.LandmarkerModelCatalog
import hk.edu.hkmu.sign.landmark.MediaPipeLandmarkExtractor
import hk.edu.hkmu.sign.landmark.VideoFrame
import hk.edu.hkmu.sign.model.SignModelCatalog
import hk.edu.hkmu.sign.pipeline.SignPipeline
import hk.edu.hkmu.sign.pipeline.SignPipelineConfig
import hk.edu.hkmu.testkit.device.DeviceConformanceSupport
import hk.edu.hkmu.testkit.device.NativeLibraryAlignment
import java.io.File
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device conformance for the sign→text path (todo 28).
 *
 * Runs the **real** MediaPipe landmark extractor and the **real** LiteRT classifier (the single
 * shipped classifier runtime) end to end on an arm64 device / x86_64 emulator, and verifies the
 * packaged natives are 16 KB page aligned. It auto-skips with a `SKIPPED:` reason when the `.task`
 * bundle, the classifier `.tflite`, or the fixture frames are not provisioned.
 *
 * Provision:
 * - `MODEL_ROOT/holistic_landmarker.task`
 * - `MODEL_ROOT/<directoryName>/<modelFile>` (TGCN `tgcn-wlasl-asl100` by default)
 * - `MODEL_ROOT/frames/<name>.png` a short ordered clip of a signed letter/isolated sign.
 */
@RunWith(AndroidJUnit4::class)
class SignDeviceConformanceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context get() = instrumentation.targetContext

    private val modelRoot = File(MODEL_ROOT)

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
    fun extractsLandmarksAndClassifiesProvisionedFixture() {
        val taskFile = File(modelRoot, LandmarkerModelCatalog.HOLISTIC_ENTRY.fileName)
        val spec = SignModelCatalog.Standard.requireClassifier(CLASSIFIER_ID)
        val classifierFile = File(File(modelRoot, spec.directoryName), spec.modelFile)
        assumeTrue("SKIPPED: MediaPipe .task not provisioned at $taskFile", taskFile.isFile)
        assumeTrue("SKIPPED: sign classifier not provisioned at $classifierFile", classifierFile.isFile)

        val framesDir = File(modelRoot, FRAMES_DIR)
        val frameFiles = framesDir.listFiles { file -> file.isFile && file.extension == "png" }
            ?.sortedBy { it.name }
            .orEmpty()
        assumeTrue("SKIPPED: no fixture PNG frames in $framesDir", frameFiles.isNotEmpty())

        val video = frameFiles.mapIndexedNotNull { index, file ->
            BitmapFactory.decodeFile(file.path)?.let { bitmap -> VideoFrame(bitmap, index * FRAME_INTERVAL_MS) }
        }
        assumeTrue("SKIPPED: fixture PNG frames could not be decoded", video.isNotEmpty())

        val extractor = MediaPipeLandmarkExtractor.create(context, modelRoot)
        val registry = SignClassifierRegistry(modelRoot)
        val pipeline = SignPipeline.create(
            extractor = extractor,
            registry = registry,
            config = SignPipelineConfig(classifierId = CLASSIFIER_ID),
            ownsExtractor = true
        )
        val startedAt = System.nanoTime()
        val results = try {
            runBlocking { pipeline.recognize(video.asFlow()).toList() }
        } finally {
            pipeline.close()
            registry.close()
        }
        val elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
        val labels = results.map { result -> result.label }
        println("TIER_B sign frames=${video.size} results=${results.size} elapsedMs=$elapsedMs labels=$labels")
        assertTrue("no sign results produced", results.isNotEmpty())
        assertTrue("empty sign label", results.all { result -> result.label.isNotBlank() })
        val perResultMs = if (results.isEmpty()) 0L else elapsedMs / results.size
        assertTrue(
            "sign window classify ${perResultMs}ms exceeds budget ${SIGN_WINDOW_BUDGET_MS}ms",
            perResultMs <= SIGN_WINDOW_BUDGET_MS
        )
    }

    private fun scanInstalledNatives(): List<NativeLibraryAlignment> {
        val apks = listOf(
            instrumentation.context.applicationInfo.sourceDir,
            context.applicationInfo.sourceDir
        ).distinct()
        return apks.flatMap { path -> DeviceConformanceSupport.scanNativeLibraries(path) }
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/sign"
        const val FRAMES_DIR = "frames"
        const val CLASSIFIER_ID = "tgcn-wlasl-asl100"
        const val FRAME_INTERVAL_MS = 33L
        const val SIGN_WINDOW_BUDGET_MS = 200L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
