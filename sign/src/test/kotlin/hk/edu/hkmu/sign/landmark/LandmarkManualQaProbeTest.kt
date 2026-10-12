@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the observable normalization contract and the adversarial
 * `MalformedFrame` for the evidence log.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :sign:testDebugUnitTest --tests '*LandmarkManualQaProbeTest' -i --rerun-tasks`.
 */
class LandmarkManualQaProbeTest {

    private val window = SignClassifierSpecs.WINDOW_SIZE
    private val keypoints = SignClassifierSpecs.TGCN_KEYPOINTS_PER_FRAME

    @Test
    fun `manual QA happy - fixture landmark stream matches the classifier contract`() {
        val onnx = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
            .normalize(LandmarkTestFixtures.window(window, keypoints))
        val tflite = LandmarkNormalizer(SignClassifierSpecs.TGCN_TFLITE)
            .normalize(LandmarkTestFixtures.window(window, keypoints))
        val range = onnx.valueRange()

        println(
            "SIGN-MANUAL-QA happy: classifier=${onnx.spec.classifierId} " +
                "shape=${onnx.shape} dtype=${onnx.dtype} elements=${onnx.elementCount}"
        )
        println(
            "SIGN-MANUAL-QA happy: classifier=${tflite.spec.classifierId} " +
                "shape=${tflite.shape} dtype=${tflite.dtype} elements=${tflite.elementCount}"
        )
        println("SIGN-MANUAL-QA happy: range=[${range.start}, ${range.endInclusive}] (expected [0.0, 1.0])")

        assertEquals(listOf(1, keypoints, window * 2), onnx.shape)
        assertEquals(listOf(1, window * 2, keypoints), tflite.shape)
        assertEquals(TensorDtype.Float32, onnx.dtype)
        assertTrue(range.start >= 0f && range.endInclusive <= 1f)
    }

    @Test
    fun `manual QA adversarial - a frame with missing landmarks is rejected`() {
        val frames = LandmarkTestFixtures.window(window, keypoints).toMutableList()
        frames[5] = LandmarkFrame(frames[5].keypoints.drop(2), timestampMillis = 5L)

        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            LandmarkNormalizer(SignClassifierSpecs.TGCN_TFLITE).normalize(frames)
        }

        println("SIGN-MANUAL-QA adversarial missing-landmarks: type=MalformedFrame reason='${exception.reason}'")
        assertTrue(exception.reason.contains("frame 5"))
    }
}
