@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.gesture

import android.content.Context
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tier A: the gesture catalog shape and the pre-native `.task` asset validation turn a
 * silently-absent bundle into a typed [AiAndroidException.ModelAssetMissing] from pure JVM code.
 *
 * The loader test needs only a framework [Context] (never the native recognizer), because
 * [MediaPipeGestureModelLoader.load] runs the asset check **before** it touches MediaPipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureModelAssetsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `catalog names the gesture model id and bundle`() {
        assertEquals("mediapipe-gesture-recognizer", GestureRecognizerModelCatalog.GESTURE_MODEL_ID)
        assertEquals("gesture_recognizer.task", GestureRecognizerModelCatalog.GESTURE_ENTRY.fileName)
        assertEquals(
            GestureRecognizerModelCatalog.GESTURE_MODEL_ID,
            GestureRecognizerModelCatalog.GESTURE_ENTRY.modelId
        )
    }

    @Test
    fun `absent bundle throws ModelAssetMissing naming the path`() {
        val missing = File(folder.root, "absent.task")

        val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            GestureModelAssets.ensurePresent(MODEL_ID, missing)
        }

        assertEquals(MODEL_ID, exception.modelId)
        assertEquals(missing.path, exception.missingAsset)
    }

    @Test
    fun `loader throws ModelAssetMissing for a missing task bundle`() {
        val missing = File(folder.root, "absent.task")

        val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            loaderFor(missing).load(registration())
        }

        assertEquals(MODEL_ID, exception.modelId)
        assertEquals(missing.path, exception.missingAsset)
    }

    @Test
    fun `non-task extension is refused`() {
        val wrong = folder.newFile("gesture_recognizer.onnx")

        assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            GestureModelAssets.ensurePresent(MODEL_ID, wrong)
        }
    }

    @Test
    fun `present task bundle is returned unchanged`() {
        val valid = folder.newFile("gesture_recognizer.task")

        assertEquals(valid, GestureModelAssets.ensurePresent(MODEL_ID, valid))
    }

    private fun loaderFor(taskFile: File): MediaPipeGestureModelLoader {
        val context: Context = RuntimeEnvironment.getApplication()
        return MediaPipeGestureModelLoader(
            context = context,
            modelId = MODEL_ID,
            taskFile = taskFile,
            minHandDetectionConfidence = 0.5f,
            minTrackingConfidence = 0.5f,
            minHandPresenceConfidence = 0.5f,
            nativeLoader = NativeLibraryLoader()
        )
    }

    private fun registration(): ModelRegistration =
        ModelRegistration(MODEL_ID, GestureRecognizerModelCatalog.MEDIAPIPE_LICENSE)

    private companion object {
        const val MODEL_ID = "mediapipe-gesture-recognizer"
    }
}
