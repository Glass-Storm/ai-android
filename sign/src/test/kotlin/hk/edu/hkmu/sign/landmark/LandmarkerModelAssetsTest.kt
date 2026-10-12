@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tier A: the pre-native `.task` asset validation turns a silently-absent bundle into a typed
 * [AiAndroidException.ModelAssetMissing] from pure JVM code.
 */
class LandmarkerModelAssetsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `absent bundle throws ModelAssetMissing naming the path`() {
        val missing = File(folder.root, "absent.task")

        val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            LandmarkerModelAssets.ensurePresent(MODEL_ID, missing)
        }

        assertEquals(MODEL_ID, exception.modelId)
        assertEquals(missing.path, exception.missingAsset)
    }

    @Test
    fun `non-task extension is refused`() {
        val wrong = folder.newFile("holistic_landmarker.onnx")

        assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            LandmarkerModelAssets.ensurePresent(MODEL_ID, wrong)
        }
    }

    @Test
    fun `present task bundle is returned unchanged`() {
        val valid = folder.newFile("holistic_landmarker.task")

        assertEquals(valid, LandmarkerModelAssets.ensurePresent(MODEL_ID, valid))
    }

    private companion object {
        const val MODEL_ID = "mediapipe-holistic-landmarker"
    }
}
