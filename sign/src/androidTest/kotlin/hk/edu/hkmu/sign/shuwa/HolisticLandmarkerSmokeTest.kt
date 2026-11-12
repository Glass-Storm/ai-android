package hk.edu.hkmu.sign.shuwa

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal MediaPipe smoke test: build the holistic landmarker and run one frame.
 *
 * This exists to isolate the Shuwa pipeline from the MediaPipe runtime itself. If this crashes, the
 * fault is in MediaPipe's native graph on this device/SDK, not in `hk.edu.hkmu.sign.shuwa`.
 *
 * Provision `/data/local/tmp/shuwa/holistic_landmarker.task` and `frame.jpg`, or the test skips.
 */
@RunWith(AndroidJUnit4::class)
class HolisticLandmarkerSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(MODEL_ROOT)

    @Test
    fun holisticGraphRunsOneImage() {
        val task = File(root, TASK_FILE)
        val fixture = File(root, FRAME_FILE)
        assumeTrue("SKIPPED: task missing at $task", task.isFile)
        assumeTrue("SKIPPED: frame missing at $fixture", fixture.isFile)
        val bitmap = BitmapFactory.decodeFile(fixture.absolutePath)
        assumeTrue("SKIPPED: frame could not be decoded", bitmap != null)

        val options =
            HolisticLandmarker.HolisticLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(task.absolutePath).build())
                .setRunningMode(RunningMode.IMAGE)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinHandLandmarksConfidence(0.5f)
                .build()
        val landmarker = HolisticLandmarker.createFromOptions(context, options)
        try {
            val image = BitmapImageBuilder(requireNotNull(bitmap)).build()
            val result =
                try {
                    landmarker.detect(image)
                } finally {
                    image.close()
                }
            println(
                "TIER_B holistic-smoke pose=${result.poseLandmarks().size} " +
                    "face=${result.faceLandmarks().size} lh=${result.leftHandLandmarks().size} " +
                    "rh=${result.rightHandLandmarks().size}"
            )
            assertNotNull(result)
        } finally {
            landmarker.close()
        }
    }

    @Test
    fun shuwaFacadeRunsOneImage() {
        val task = File(root, TASK_FILE)
        val fixture = File(root, FRAME_FILE)
        assumeTrue("SKIPPED: task missing at $task", task.isFile)
        assumeTrue("SKIPPED: frame missing at $fixture", fixture.isFile)
        val bitmap = BitmapFactory.decodeFile(fixture.absolutePath)
        assumeTrue("SKIPPED: frame could not be decoded", bitmap != null)

        val landmarker = ShuwaLandmarker.create(context = context, modelDir = root)
        try {
            val frame = landmarker.detect(requireNotNull(bitmap), 0L)
            println(
                "TIER_B shuwa-facade pose=${frame.pose.size} face=${frame.face.size} " +
                    "lh=${frame.leftHand.size} rh=${frame.rightHand.size}"
            )
        } finally {
            landmarker.close()
        }
    }

    private companion object {
        const val MODEL_ROOT = "/data/local/tmp/shuwa"
        const val TASK_FILE = "holistic_landmarker.task"
        const val FRAME_FILE = "frame.jpg"
    }
}
