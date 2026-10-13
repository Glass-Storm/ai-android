package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Host-JVM coverage of the diarization asset pre-validation, which must fire **before** any native
 * call so a missing graph is a typed [AiAndroidException.ModelAssetMissing].
 */
class DiarizationModelAssetsTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val spec = DiarizationModels.Pyannote3Dspeaker

    @Test
    fun `resolves both graphs when present`() {
        val root = temp.newFolder("models")
        val segmentation = presentSegmentation(root)
        val embedding = presentEmbedding(root)

        val resolved = DiarizationModelAssets.ensurePresent(spec, root)

        assertEquals(segmentation, resolved.segmentationFile)
        assertEquals(embedding, resolved.embeddingFile)
    }

    @Test
    fun `missing segmentation graph is reported by name`() {
        val root = temp.newFolder("models")
        presentEmbedding(root)

        val exception = assertMissing(root)

        assertEquals(spec.segmentationFile, exception.missingAsset)
        assertEquals(spec.id, exception.modelId)
    }

    @Test
    fun `missing embedding graph is reported by name`() {
        val root = temp.newFolder("models")
        presentSegmentation(root)

        val exception = assertMissing(root)

        assertEquals(spec.embeddingFile, exception.missingAsset)
        assertEquals(spec.id, exception.modelId)
    }

    @Test
    fun `model root that is not a directory is rejected`() {
        val missing = File(temp.root, "does-not-exist")

        val exception = assertMissing(missing)

        assertEquals(missing.path, exception.missingAsset)
    }

    private fun assertMissing(root: File): AiAndroidException.ModelAssetMissing =
        assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            DiarizationModelAssets.ensurePresent(spec, root)
        }

    private fun presentSegmentation(root: File): File {
        val directory = File(root, spec.segmentationDirectory)
        assertTrue(directory.mkdirs())
        return File(directory, spec.segmentationFile).apply { writeText("segmentation") }
    }

    private fun presentEmbedding(root: File): File = File(root, spec.embeddingFile).apply { writeText("embedding") }
}
