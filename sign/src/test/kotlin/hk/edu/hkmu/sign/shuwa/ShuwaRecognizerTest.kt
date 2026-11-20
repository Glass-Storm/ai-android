package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.sign.landmark.Keypoint3D
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the recogniser facade: windowing cadence, the motion gate and the prediction
 * mapping. The translator is a lambda here, so no native runtime is needed.
 */
class ShuwaRecognizerTest {

    @Test
    fun `one prediction is emitted per completed window`() = runTest {
        val recogniser = recogniser(embedding = floatArrayOf(0f, 1f, 0f))

        val predictions = recogniser.recognize(flowOf(*movingFrames(20).toTypedArray())).toList()

        assertEquals(2, predictions.size)
        assertEquals("Hksl_bathroom", predictions.first().label)
        assertEquals(0L, predictions.first().windowStartMillis)
        assertEquals(15L * FRAME_MILLIS, predictions.first().windowEndMillis)
    }

    @Test
    fun `a motionless window is not classified`() = runTest {
        val recogniser = recogniser(embedding = floatArrayOf(0f, 1f, 0f))

        val predictions = recogniser.recognize(flowOf(*stillFrames(20).toTypedArray())).toList()

        assertEquals(0, predictions.size)
    }

    @Test
    fun `a low-confidence match is flagged but still emitted`() = runTest {
        val recogniser = recogniser(embedding = floatArrayOf(0f, 1f, 0f), confidenceThreshold = 1.1f)

        val predictions = recogniser.recognize(flowOf(*movingFrames(16).toTypedArray())).toList()

        assertEquals(1, predictions.size)
        assertTrue(predictions.first().lowConfidence)
    }

    @Test
    fun `a completed window is not resampled a second time`() = runTest {
        var captured: ShuwaInputs? = null
        val recogniser =
            ShuwaRecognizer(
                embedder = ShuwaEmbeddingModel { inputs ->
                    captured = inputs
                    floatArrayOf(0f, 1f, 0f)
                },
                index = ShuwaKnnIndex.load(table()),
                config = ShuwaRecognizerConfig(),
                dispatcher = Dispatchers.Unconfined
            )

        recogniser.recognize(flowOf(*movingFrames(16).toTypedArray())).toList()

        val inputs = requireNotNull(captured)
        assertEquals(0f, inputs.pose[0], TOLERANCE)
        assertEquals(
            1.5f,
            inputs.pose[(ShuwaSpec.WINDOW_FRAMES - 1) * ShuwaSpec.POSE_ELEMENTS],
            TOLERANCE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `width must match the translator window`() {
        ShuwaRecognizerConfig(width = 8)
    }

    private fun recogniser(embedding: FloatArray, confidenceThreshold: Float = 0.5f): ShuwaRecognizer = ShuwaRecognizer(
        embedder = ShuwaEmbeddingModel { embedding },
        index = ShuwaKnnIndex.load(table()),
        config = ShuwaRecognizerConfig(stride = 4, confidenceThreshold = confidenceThreshold),
        dispatcher = Dispatchers.Unconfined
    )

    private fun movingFrames(count: Int): List<ShuwaFrame> = List(count) { index -> frame(index, x = index * 0.1f) }

    private fun stillFrames(count: Int): List<ShuwaFrame> = List(count) { index -> frame(index, x = 0f) }

    private fun frame(index: Int, x: Float): ShuwaFrame = ShuwaFrame(
        pose = List(ShuwaSpec.POSE_POINTS) { point -> ShuwaPosePoint(x, point * 0.01f, 0f, 0.9f) },
        face = List(ShuwaSpec.FACE_POINTS) { point -> Keypoint3D(x, point * 0.01f, 0f) },
        leftHand = List(ShuwaSpec.HAND_POINTS) { point -> Keypoint3D(x, point * 0.01f, 0f) },
        rightHand = List(ShuwaSpec.HAND_POINTS) { point -> Keypoint3D(x, point * 0.01f, 0f) },
        timestampMillis = index.toLong() * FRAME_MILLIS
    )

    /** Two-row, three-dimension table with the same layout the real exporter writes. */
    private fun table(): File {
        val labels = listOf("Hksl_able_to", "Hksl_bathroom")
        val buffer = ByteBuffer.allocate(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC)
        buffer.putShort(labels.size.toShort())
        buffer.putInt(ROWS)
        buffer.putShort(DIMENSIONS)
        labels.forEach { label ->
            val raw = label.toByteArray(Charsets.UTF_8)
            buffer.putShort(raw.size.toShort())
            buffer.put(raw)
        }
        repeat(ROWS) { row -> buffer.putShort(row.toShort()) }
        buffer.putShort(HALF_ONE)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ONE)
        buffer.putShort(HALF_ZERO)
        val file = File.createTempFile("shuwa-recogniser", ".bin")
        file.deleteOnExit()
        file.writeBytes(buffer.array().copyOf(buffer.position()))
        return file
    }

    private companion object {
        const val TOLERANCE = 1e-4f
        const val FRAME_MILLIS = 66L
        const val BUFFER_BYTES = 256
        const val ROWS = 2
        const val DIMENSIONS: Short = 3
        const val HALF_ZERO: Short = 0x0000
        const val HALF_ONE: Short = 0x3C00
        val MAGIC: ByteArray = "SKS1".toByteArray(Charsets.US_ASCII)
    }
}
