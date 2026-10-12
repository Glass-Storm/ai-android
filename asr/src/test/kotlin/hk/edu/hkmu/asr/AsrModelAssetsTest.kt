package hk.edu.hkmu.asr

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tier A acceptance for the pre-native asset manifest ([AsrModelAssets]).
 *
 * These tests run on the host JVM and never touch native: the whole point (Wave-0 S4) is that a
 * missing asset must raise a typed `ModelAssetMissing` before any sherpa call.
 */
class AsrModelAssetsTest {

    @Test
    fun `missing model onnx yields ModelAssetMissing before native`() {
        val dir = newTempDir()
        try {
            File(dir, AsrModelAssets.TOKENS_FILE).writeText("tokens")

            val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
                AsrModelAssets.ensurePresent(MODEL_ID, dir)
            }

            assertEquals(MODEL_ID, exception.modelId)
            assertEquals(AsrModelAssets.MODEL_GLOB, exception.missingAsset)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing tokens yields ModelAssetMissing`() {
        val dir = newTempDir()
        try {
            File(dir, "model.int8.onnx").writeText("model")

            val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
                AsrModelAssets.ensurePresent(MODEL_ID, dir)
            }

            assertEquals(AsrModelAssets.TOKENS_FILE, exception.missingAsset)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `present assets resolve to concrete files`() {
        val dir = newTempDir()
        try {
            File(dir, "model.int8.onnx").writeText("model")
            File(dir, AsrModelAssets.TOKENS_FILE).writeText("tokens")

            val resolved = AsrModelAssets.ensurePresent(MODEL_ID, dir)

            assertEquals("model.int8.onnx", resolved.modelFile.name)
            assertEquals(AsrModelAssets.TOKENS_FILE, resolved.tokensFile.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `nonexistent directory yields ModelAssetMissing`() {
        val dir = newTempDir()
        dir.delete()

        assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            AsrModelAssets.ensurePresent(MODEL_ID, dir)
        }
    }

    private fun newTempDir(): File = Files.createTempDirectory("asr-assets").toFile()

    private companion object {
        const val MODEL_ID = "test-model"
    }
}
