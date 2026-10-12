@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A: the normalization adapter's exact classifier contract (shape / dtype / range / ordering)
 * and its typed failure on a malformed frame. Pure JVM — no MediaPipe native.
 */
class LandmarkNormalizerTest {

    private val window = SignClassifierSpecs.WINDOW_SIZE
    private val keypoints = SignClassifierSpecs.TGCN_KEYPOINTS_PER_FRAME

    @Test
    fun `tgcn onnx spec produces node-major 1x55x100 float32 in range`() {
        val spec = SignClassifierSpecs.TGCN_ONNX
        val tensor = LandmarkNormalizer(spec).normalize(LandmarkTestFixtures.window(window, keypoints))

        assertEquals(listOf(1, keypoints, window * 2), tensor.shape)
        assertEquals(TensorDtype.Float32, tensor.dtype)
        assertEquals(keypoints * window * 2, tensor.elementCount)

        val range = tensor.valueRange()
        assertTrue("min ${range.start} below 0", range.start >= 0f)
        assertTrue("max ${range.endInclusive} above 1", range.endInclusive <= 1f)
    }

    @Test
    fun `node-major layout interleaves x then y per keypoint across the window`() {
        val spec = SignClassifierSpecs.TGCN_ONNX
        val tensor = LandmarkNormalizer(spec).normalize(LandmarkTestFixtures.window(window, keypoints))
        val featureLength = spec.nodeFeatureLength

        for (keypoint in listOf(0, 1, keypoints - 1)) {
            for (frame in 0 until window) {
                val base = keypoint * featureLength
                assertEquals(LandmarkTestFixtures.x(keypoint, frame), tensor.data[base + frame], TOLERANCE)
                assertEquals(LandmarkTestFixtures.y(keypoint, frame), tensor.data[base + window + frame], TOLERANCE)
            }
        }
    }

    @Test
    fun `tflite spec is the exact transpose of the onnx layout`() {
        val onnx = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
            .normalize(LandmarkTestFixtures.window(window, keypoints))
        val tflite = LandmarkNormalizer(SignClassifierSpecs.TGCN_TFLITE)
            .normalize(LandmarkTestFixtures.window(window, keypoints))

        assertEquals(listOf(1, window * 2, keypoints), tflite.shape)
        assertEquals(TensorDtype.Float32, tflite.dtype)

        for (feature in 0 until window * 2) {
            for (keypoint in 0 until keypoints) {
                val onnxValue = onnx.data[keypoint * window * 2 + feature]
                assertEquals(onnxValue, tflite.data[feature * keypoints + keypoint], 0f)
            }
        }
    }

    @Test
    fun `out-of-range coordinates are clamped into the spec range`() {
        val spec = SignClassifierSpecs.TGCN_TFLITE
        val frame = LandmarkFrame(
            keypoints = List(keypoints) { Keypoint2D(1.5f, -0.5f) },
            timestampMillis = 0L
        )

        val tensor = LandmarkNormalizer(spec).normalize(List(window) { frame })

        assertEquals(0f, tensor.valueRange().start, 0f)
        assertEquals(1f, tensor.valueRange().endInclusive, 0f)
    }

    @Test
    fun `a frame with missing landmarks throws MalformedFrame`() {
        val frames = LandmarkTestFixtures.window(window, keypoints).toMutableList()
        frames[7] = LandmarkFrame(frames[7].keypoints.drop(1), timestampMillis = 7L)

        val normalizer = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) { normalizer.normalize(frames) }
        assertTrue(exception.reason.contains("frame 7"))
    }

    @Test
    fun `a frame with no landmarks throws MalformedFrame`() {
        val frames = LandmarkTestFixtures.window(window, keypoints).toMutableList()
        frames[0] = LandmarkFrame(emptyList(), timestampMillis = 0L)

        val normalizer = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
        assertThrows(AiAndroidException.MalformedFrame::class.java) { normalizer.normalize(frames) }
    }

    @Test
    fun `a short window throws MalformedFrame`() {
        val frames = LandmarkTestFixtures.window(window - 1, keypoints)
        val normalizer = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) { normalizer.normalize(frames) }
        assertTrue(exception.reason.contains("$window-frame window"))
    }

    @Test
    fun `a non-finite coordinate throws MalformedFrame`() {
        val frames = LandmarkTestFixtures.window(window, keypoints).toMutableList()
        val broken = frames[3].keypoints.toMutableList()
        broken[0] = Keypoint2D(Float.NaN, 0.5f)
        frames[3] = LandmarkFrame(broken, timestampMillis = 3L)

        val normalizer = LandmarkNormalizer(SignClassifierSpecs.TGCN_ONNX)
        assertThrows(AiAndroidException.MalformedFrame::class.java) { normalizer.normalize(frames) }
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
