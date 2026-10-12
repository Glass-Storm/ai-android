@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tier A acceptance for the pre-native model-asset validation. */
class NllbModelAssetsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `non-directory is reported as a missing asset`() {
        val missing = File(folder.root, "absent")

        val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            NllbModelAssets.ensurePresent("nllb", missing)
        }

        assertEquals("nllb", exception.modelId)
        assertEquals(missing.path, exception.missingAsset)
    }

    @Test
    fun `a missing graph is reported by its glob`() {
        val dir = folder.newFolder("model")
        File(dir, "encoder_model_int8.onnx").writeText("stub")

        val exception = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            NllbModelAssets.ensurePresent("nllb", dir)
        }

        assertEquals(NllbModelAssets.DECODER_GLOB, exception.missingAsset)
    }

    @Test
    fun `a complete directory resolves every asset`() {
        val dir = folder.newFolder("model")
        File(dir, "encoder_model_int8.onnx").writeText("stub")
        File(dir, "decoder_model_int8.onnx").writeText("stub")
        File(dir, "sentencepiece.bpe.model").writeText("stub")
        File(dir, "tokenizer_config.json").writeText("stub")

        val assets = NllbModelAssets.ensurePresent("nllb", dir)

        assertTrue(assets.encoderFile.name.startsWith("encoder_model"))
        assertTrue(assets.decoderFile.name.startsWith("decoder_model"))
        assertEquals(NllbModelAssets.SENTENCEPIECE_FILE, assets.sentencePieceFile.name)
        assertEquals(NllbModelAssets.TOKENIZER_CONFIG_FILE, assets.tokenizerConfigFile.name)
    }
}
