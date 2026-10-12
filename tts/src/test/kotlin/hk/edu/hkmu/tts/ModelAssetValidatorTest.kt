@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.tts.asset.AssetProbe
import hk.edu.hkmu.tts.asset.ModelAssetValidator
import hk.edu.hkmu.tts.model.CantoneseVits
import hk.edu.hkmu.tts.model.MandarinVits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelAssetValidatorTest {

    private val root = File("/models")

    @Test
    fun resolvesEveryAssetForCantonese() {
        val model = ModelAssetValidator(probeWith(ALL_ASSET_NAMES)).validate(CantoneseVits, root)

        assertEquals(CantoneseVits, model.spec)
        assertEquals("vits-cantonese-hf-xiaomaiiwn", model.directory.name)
        assertEquals("vits-cantonese-hf-xiaomaiiwn.onnx", model.modelPath.name)
        assertEquals("tokens.txt", model.tokensPath.name)
        assertEquals("lexicon.txt", model.lexiconPath?.name)
        assertEquals("rule.fst", model.ruleFstPath?.name)
    }

    @Test
    fun autoDetectsModelPrefixedOnnx() {
        val probe = probeWith(listOf("model.onnx", "tokens.txt", "lexicon.txt"))

        val model = ModelAssetValidator(probe).validate(MandarinVits, root)

        assertEquals("model.onnx", model.modelPath.name)
    }

    @Test
    fun missingTokensThrowsTypedError() {
        val probe = probeWith(listOf("vits-cantonese-hf-xiaomaiiwn.onnx", "lexicon.txt", "rule.fst"))

        val error = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            ModelAssetValidator(probe).validate(CantoneseVits, root)
        }

        assertEquals("vits-cantonese-hf-xiaomaiiwn", error.modelId)
        assertEquals("tokens.txt", error.missingAsset)
    }

    @Test
    fun missingLexiconThrowsTypedError() {
        val probe = probeWith(listOf("vits-cantonese-hf-xiaomaiiwn.onnx", "tokens.txt", "rule.fst"))

        val error = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            ModelAssetValidator(probe).validate(CantoneseVits, root)
        }

        assertEquals("lexicon.txt", error.missingAsset)
    }

    @Test
    fun missingRuleFstThrowsTypedError() {
        val probe = probeWith(listOf("vits-cantonese-hf-xiaomaiiwn.onnx", "tokens.txt", "lexicon.txt"))

        val error = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            ModelAssetValidator(probe).validate(CantoneseVits, root)
        }

        assertEquals("rule.fst", error.missingAsset)
    }

    @Test
    fun missingModelGlobThrowsTypedError() {
        val probe = probeWith(listOf("tokens.txt", "lexicon.txt"))

        val error = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            ModelAssetValidator(probe).validate(MandarinVits, root)
        }

        assertEquals("model*.onnx", error.missingAsset)
    }

    @Test
    fun missingDirectoryThrowsTypedError() {
        val probe = AssetProbe { emptyList() }

        val error = assertThrows(AiAndroidException.ModelAssetMissing::class.java) {
            ModelAssetValidator(probe).validate(MandarinVits, root)
        }

        assertEquals("model*.onnx", error.missingAsset)
    }
}
