package hk.edu.hkmu.sign.shuwa

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device conformance for the Shuwa HKSL path: the **real** MediaPipe holistic landmarker, the
 * **real** LiteRT translator and the **real** `SKS1` KNN table, end to end on an arm64 device.
 *
 * Everything is auto-skipped with a `SKIPPED:` reason when a provisioned artifact is missing, so the
 * suite stays green on a bare device while proving the pipeline wherever the artifacts exist.
 *
 * Provision under `/data/local/tmp/shuwa/`:
 * - `holistic_landmarker.task` (MediaPipe Tasks Vision, Apache-2.0)
 * - `shuwa-translator-f32.tflite` (host-exported translator)
 * - `shuwa-knn-hksl.bin` (HKSL-only KNN table)
 * - `frame.jpg` (one frame containing a person)
 */
@RunWith(AndroidJUnit4::class)
class ShuwaDeviceConformanceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * `/data/local/tmp` first, matching the repository's other device tests; the app-scoped external
     * directory is only a fallback. On Android 11+ scoped storage can make that external path
     * unreadable from the app process (`exists=true`, `canRead=false`), so it is not the default.
     */
    private val root: File =
        listOf(File(MODEL_ROOT), context.getExternalFilesDir(null)?.resolve(DIR_NAME))
            .filterNotNull()
            .firstOrNull { it.isDirectory } ?: File(MODEL_ROOT)

    @Test
    fun landmarkerExtractsPoseFaceAndHands() {
        val task = File(root, TASK_FILE)
        val fixture = File(root, FRAME_FILE)
        assumeTrue("SKIPPED: holistic .task missing at $task (${diagnostics()})", task.isFile)
        assumeTrue("SKIPPED: fixture frame missing at $fixture (${diagnostics()})", fixture.isFile)

        val bitmap = BitmapFactory.decodeFile(fixture.absolutePath)
        assumeTrue("SKIPPED: fixture could not be decoded", bitmap != null)
        val landmarker = ShuwaLandmarker.create(context = context, modelDir = root)
        try {
            val frame = landmarker.detect(requireNotNull(bitmap), FIRST_FRAME_MILLIS)
            println(
                "TIER_B shuwa-landmarks pose=${frame.pose.size} face=${frame.face.size} " +
                    "lh=${frame.leftHand.size} rh=${frame.rightHand.size}"
            )
            assertEquals("pose must be the 33-point BlazePose mesh", POSE_POINTS, frame.pose.size)
            assertTrue("face mesh must be present", frame.face.size >= POSE_FACE_MIN)
            assertTrue(
                "a hand is either absent or the full 21-point mesh",
                frame.leftHand.isEmpty() || frame.leftHand.size == HAND_POINTS
            )
        } finally {
            landmarker.close()
        }
    }

    @Test
    fun windowEmbedsToThreeHundredThirtySixAndMatchesAnHkslGloss() {
        val task = File(root, TASK_FILE)
        val fixture = File(root, FRAME_FILE)
        val translator = File(root, TRANSLATOR_FILE)
        val knn = File(root, KNN_FILE)
        assumeTrue("SKIPPED: holistic .task missing at $task (${diagnostics()})", task.isFile)
        assumeTrue("SKIPPED: fixture frame missing at $fixture (${diagnostics()})", fixture.isFile)
        assumeTrue("SKIPPED: translator missing at $translator (${diagnostics()})", translator.isFile)
        assumeTrue("SKIPPED: KNN table missing at $knn (${diagnostics()})", knn.isFile)

        val bitmap = BitmapFactory.decodeFile(fixture.absolutePath)
        assumeTrue("SKIPPED: fixture could not be decoded", bitmap != null)
        val landmarker = ShuwaLandmarker.create(context = context, modelDir = root)
        try {
            // A real pipeline hands over a NEW frame per capture. Closing an MPImage can recycle the
            // backing bitmap, so reusing one Bitmap across detects is not representative (and crashes).
            val frames =
                List(ShuwaSpec.WINDOW_FRAMES) { index ->
                    val frameImage = requireNotNull(bitmap).copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    landmarker.detect(frameImage, index * FRAME_INTERVAL_MILLIS)
                }
            // LiteRT is loaded only after MediaPipe has finished with this window: the two runtimes
            // are initialised separately in the test to keep their native state isolated.
            val embedder =
                TfliteShuwaEmbedder.load(
                    modelFile = translator,
                    license = ModelLicense(LicenseKind.UserProvided)
                )
            val inputs = ShuwaPreprocessor.prepare(frames)
            val embedding = embedder.embed(inputs)
            val match = ShuwaKnnIndex.load(knn).match(embedding)
            println(
                "TIER_B shuwa-match label=${match.label} confidence=${match.confidence} " +
                    "distance=${match.distance} norm=${embedding.size}"
            )
            assertEquals(ShuwaSpec.EMBEDDING_DIMENSIONS, embedding.size)
            assertTrue("embedding must be finite", embedding.all { it.isFinite() })
            assertTrue("label must be an HKSL gloss", match.label.startsWith("Hksl_"))
            embedder.close()
        } finally {
            landmarker.close()
        }
    }

    /** Diagnostic for the SKIPPED message: what THIS process can see under [root]. */
    private fun diagnostics(): String {
        val listing = runCatching { root.listFiles()?.joinToString(",") { it.name } ?: "null" }
            .getOrElse { "error:${it.javaClass.simpleName}" }
        return "root=$root exists=${root.exists()} canRead=${root.canRead()} listing=$listing"
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/shuwa"
        const val DIR_NAME = "shuwa"
        const val TASK_FILE = "holistic_landmarker.task"
        const val TRANSLATOR_FILE = "shuwa-translator-f32.tflite"
        const val KNN_FILE = "shuwa-knn-hksl.bin"
        const val FRAME_FILE = "frame.jpg"
        const val FIRST_FRAME_MILLIS = 0L
        const val FRAME_INTERVAL_MILLIS = 66L
        const val POSE_POINTS = 33
        const val POSE_FACE_MIN = 468
        const val HAND_POINTS = 21
    }
}
