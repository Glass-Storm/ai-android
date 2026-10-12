@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.sign.model.SignInputLayout
import hk.edu.hkmu.sign.model.SignModelSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SignWindowAdapterTest {

    private val frameFeaturesSpec = spec(SignInputLayout.FrameFeatures(featuresPerFrame = 3), windowFrames = 2)
    private val transposedSpec = spec(SignInputLayout.TransposedNodes(nodes = 2, dimsPerNode = 2), windowFrames = 3)

    @Test
    fun `frame features layout concatenates each frame row major`() {
        val window = listOf(frame(1f, 2f, 3f), frame(4f, 5f, 6f))

        val tensor = SignWindowAdapter.assemble(window, frameFeaturesSpec)

        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), tensor, 0f)
    }

    @Test
    fun `transposed nodes layout matches the onnx2tf node-feature-major contract`() {
        val window = listOf(
            frame(1f, 2f, 3f, 4f),
            frame(5f, 6f, 7f, 8f),
            frame(9f, 10f, 11f, 12f)
        )

        val tensor = SignWindowAdapter.assemble(window, transposedSpec)

        // node0 x over 3 frames, node1 x, node0 y, node1 y
        assertArrayEquals(floatArrayOf(1f, 3f, 5f, 7f, 9f, 11f, 2f, 4f, 6f, 8f, 10f, 12f), tensor, 0f)
        assertEquals(transposedSpec.inputElements, tensor.size)
    }

    @Test
    fun `a short frame raises a typed malformed frame`() {
        val window = listOf(frame(1f, 2f, 3f), frame(4f, 5f))

        assertThrows(AiAndroidException.MalformedFrame::class.java) {
            SignWindowAdapter.assemble(window, frameFeaturesSpec)
        }
    }

    private fun frame(vararg values: Float): SignFrame = SignFrame(values, 0L)

    private fun spec(layout: SignInputLayout, windowFrames: Int): SignModelSpec = SignModelSpec(
        classifierId = "test",
        language = Language("asl"),
        license = ModelLicense(LicenseKind.Permissive),
        directoryName = "test",
        modelFile = "model.tflite",
        windowFrames = windowFrames,
        inputLayout = layout,
        classCount = 1,
        labels = listOf("x"),
        sourceUrl = "https://example.com/model.tflite",
        shipped = false
    )
}
