@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.tts.asset.ModelAssetValidator
import hk.edu.hkmu.tts.model.TtsModelCatalog
import hk.edu.hkmu.tts.pcm.toInt16
import hk.edu.hkmu.tts.synth.VitsSynthesizerFactory
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaTtsEngineTest {

    @Test
    fun fakePathEmitsOrderedNonEmptyInt16Chunks() = runTest {
        val fake = FakeVitsSynthesizer()
        val engine = engineFor(Language.Cantonese, factoryReturning(fake))

        val chunks = engine.synthesizeInt16("你好，今日天氣好好。").toList()

        assertTrue("chunks must be non-empty", chunks.isNotEmpty())
        assertTrue("every chunk must carry samples", chunks.all { it.samples.isNotEmpty() })
        assertTrue("utterance must split into >1 ordered chunk", chunks.size > 1)
        assertEquals(RAMP_SAMPLE_COUNT, chunks.sumOf { it.samples.size })
        assertEquals("model-reported rate", CANTONESE_RATE_HZ, chunks.first().sampleRateHz)
        assertTrue("every chunk keeps the model-reported rate", chunks.all { it.sampleRateHz == CANTONESE_RATE_HZ })
        assertTrue("every chunk keeps the speaker id", chunks.all { it.speakerId == 3 })

        val joined = ShortArray(chunks.sumOf { it.samples.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.samples.copyInto(joined, offset)
            offset += chunk.samples.size
        }
        assertArrayEquals(toInt16(RAMP_SAMPLES), joined)
    }

    @Test
    fun synthesizeEmitsFloat32ChunksAtModelReportedRate() = runTest {
        val engine = engineFor(Language.Cantonese, factoryReturning(FakeVitsSynthesizer()))

        val chunks = engine.synthesize("你好").toList()

        assertEquals(RAMP_SAMPLE_COUNT, chunks.sumOf { it.samples.size })
        assertEquals(CANTONESE_RATE_HZ, chunks.first().sampleRateHz)
        assertTrue(chunks.flatMap { it.samples.toList() }.all { it in -1.0f..1.0f })
    }

    @Test
    fun unsupportedLanguageThrowsTypedError() = runTest {
        val engine = engineFor(Language.Japanese, factoryReturning(FakeVitsSynthesizer()))

        val error = runCatching { engine.synthesizeInt16("テスト").toList() }.exceptionOrNull()

        assertTrue("expected UnsupportedLanguage but was $error", error is AiAndroidException.UnsupportedLanguage)
        val typed = error as AiAndroidException.UnsupportedLanguage
        assertEquals(Language.Japanese, typed.language)
    }

    @Test
    fun missingAssetsFailBeforeTheNativeFactory() = runTest {
        var factoryInvoked = false
        val engine = SherpaTtsEngine(
            config = TtsConfig(language = Language.Cantonese),
            modelRoot = File("/nonexistent-models"),
            catalog = TtsModelCatalog.Standard,
            validator = ModelAssetValidator(probeWith(listOf("tokens.txt"))),
            synthesizerFactory = VitsSynthesizerFactory { _, _ ->
                factoryInvoked = true
                FakeVitsSynthesizer()
            }
        )

        val error = runCatching { engine.synthesizeInt16("你好").toList() }.exceptionOrNull()

        assertTrue("expected ModelAssetMissing but was $error", error is AiAndroidException.ModelAssetMissing)
        assertEquals(
            "vits-cantonese-hf-xiaomaiiwn.onnx",
            (error as AiAndroidException.ModelAssetMissing).missingAsset
        )
        assertFalse("native factory must not run when assets are missing", factoryInvoked)
    }

    @Test
    fun synthesizerIsLoadedOnceAndReused() = runTest {
        var creates = 0
        val engine = engineFor(
            Language.Cantonese,
            factory = VitsSynthesizerFactory { _, _ ->
                creates++
                FakeVitsSynthesizer()
            }
        )

        engine.synthesizeInt16("第一句").toList()
        engine.synthesizeInt16("第二句").toList()

        assertEquals("one native session per language", 1, creates)
    }

    @Test
    fun voiceInfoReportsModelMetadata() = runTest {
        val fake = FakeVitsSynthesizer(sampleRateHz = 24_000, numSpeakers = 5)
        val engine = engineFor(Language.Mandarin, factoryReturning(fake))

        val info = engine.voiceInfo()

        assertEquals(Language.Mandarin, info.language)
        assertEquals(24_000, info.sampleRateHz)
        assertEquals(5, info.numSpeakers)
        assertEquals(3, info.speakerId)
    }

    @Test
    fun closeReleasesLoadedSynthesizers() = runTest {
        val fake = FakeVitsSynthesizer()
        val engine = engineFor(Language.Cantonese, factoryReturning(fake))
        engine.synthesizeInt16("你好").toList()

        engine.close()

        assertTrue(fake.closed)
        assertNotNull(fake.lastText)
    }

    @Test
    fun explicitGpuAcceleratorIsRejected() = runTest {
        val engine = engineFor(
            Language.Cantonese,
            factoryReturning(FakeVitsSynthesizer()),
            config = TtsConfig(
                language = Language.Cantonese,
                runtime = hk.edu.hkmu.core.config.EngineRuntimeConfig(
                    accelerator = hk.edu.hkmu.core.config.Accelerator.Gpu
                )
            )
        )

        val error = runCatching { engine.synthesizeInt16("你好").toList() }.exceptionOrNull()

        assertTrue(error is AiAndroidException.AcceleratorUnavailable)
    }
}
