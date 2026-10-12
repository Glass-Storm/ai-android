@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.tts.asset.ModelAssetValidator
import hk.edu.hkmu.tts.model.TtsModelCatalog
import hk.edu.hkmu.tts.synth.VitsSynthesizerFactory
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA probe. Prints observable values to stdout; run with `-i --rerun-tasks` so Gradle
 * forwards the `STANDARD_OUT` block into the evidence log.
 */
class SherpaTtsEngineManualQaTest {

    @Test
    fun manualQaFakePathEmitsOrderedInt16() = runTest {
        val engine = engineFor(Language.Cantonese, factoryReturning(FakeVitsSynthesizer()))

        val chunks = engine.synthesizeInt16("你好，今日天氣好好。").toList()

        println("MANUAL_QA tts.fake.chunkCount=${chunks.size}")
        println("MANUAL_QA tts.fake.totalSamples=${chunks.sumOf { it.samples.size }}")
        println("MANUAL_QA tts.fake.chunkSizes=${chunks.joinToString(",") { it.samples.size.toString() }}")
        println("MANUAL_QA tts.fake.sampleRateHz=${chunks.first().sampleRateHz}")
        println("MANUAL_QA tts.fake.speakerId=${chunks.first().speakerId}")
        println("MANUAL_QA tts.fake.nonEmpty=${chunks.all { it.samples.isNotEmpty() }}")

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.samples.isNotEmpty() })
    }

    @Test
    fun manualQaUnsupportedLanguageIsTyped() = runTest {
        val engine = engineFor(Language.Japanese, factoryReturning(FakeVitsSynthesizer()))

        val error = runCatching { engine.synthesizeInt16("テスト").toList() }.exceptionOrNull()

        println("MANUAL_QA tts.unsupported.errorClass=${error?.let { it::class.java.simpleName }}")
        println(
            "MANUAL_QA tts.unsupported.language=" +
                (error as? AiAndroidException.UnsupportedLanguage)?.language?.tag
        )

        assertTrue(error is AiAndroidException.UnsupportedLanguage)
    }

    @Test
    fun manualQaMissingAssetIsTypedBeforeNative() = runTest {
        val engine = SherpaTtsEngine(
            config = TtsConfig(language = Language.Cantonese),
            modelRoot = File("/nonexistent-models"),
            catalog = TtsModelCatalog.Standard,
            validator = ModelAssetValidator(probeWith(listOf("tokens.txt"))),
            synthesizerFactory = VitsSynthesizerFactory { _, _ -> FakeVitsSynthesizer() }
        )

        val error = runCatching { engine.synthesizeInt16("你好").toList() }.exceptionOrNull()

        println("MANUAL_QA tts.missingAsset.errorClass=${error?.let { it::class.java.simpleName }}")
        println("MANUAL_QA tts.missingAsset.missing=${(error as? AiAndroidException.ModelAssetMissing)?.missingAsset}")

        assertTrue(error is AiAndroidException.ModelAssetMissing)
    }
}
