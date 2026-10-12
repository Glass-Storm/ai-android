package hk.edu.hkmu.core.di

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.engine.TtsEngine
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication

/**
 * Manual QA for the DI facade (todo 23).
 *
 * Proves the three acceptance claims: the facade assembles every engine from the shared `testkit`
 * fakes; a host with manual DI constructs every engine without Koin; and a missing required
 * dependency fails with the typed `AiAndroidException.MissingDependency`. The optional Koin module is
 * additionally resolved to show it publishes the same engines.
 */
class AiAndroidFacadeTest {

    @Test
    fun `facade assembles every engine from the testkit fakes`() {
        val fakes = FakeEngineFactory()

        val graph = AiAndroidGraph.builder()
            .factory(fakes)
            .asr(AsrConfig())
            .tts(TtsConfig())
            .translation(TranslationConfig())
            .sign(SignConfig(classifierId = CLASSIFIER_ID))
            .postProcess(PostProcessConfig())
            .build()

        assertSame(fakes.asr, graph.asr())
        assertSame(fakes.tts, graph.tts())
        assertSame(fakes.translation, graph.translation())
        assertSame(fakes.sign, graph.sign())
        assertSame(fakes.postProcessor, graph.postProcessor())

        // The factory received exactly one config per stage, proving the builder wired them through.
        assertEquals(1, fakes.asrConfigs.size)
        assertEquals(1, fakes.ttsConfigs.size)
        assertEquals(1, fakes.translationConfigs.size)
        assertEquals(1, fakes.signConfigs.size)
        assertEquals(1, fakes.postProcessConfigs.size)
        assertEquals(CLASSIFIER_ID, fakes.signConfigs.single().classifierId)

        assertTrue(graph.has(EngineSlot.Asr))
        assertTrue(graph.has(EngineSlot.PostProcess))
    }

    @Test
    fun `a host with manual DI constructs every engine without Koin`() {
        // No container and no EngineFactory: the host owns instances and passes them in directly.
        val asr = FakeEngineFactory().asr
        val tts = FakeEngineFactory().tts
        val translation = FakeEngineFactory().translation
        val sign = FakeEngineFactory().sign
        val postProcessor = FakeEngineFactory().postProcessor

        val graph: AiAndroidEntryPoint = AiAndroidGraph.of(
            asr = asr,
            tts = tts,
            translation = translation,
            sign = sign,
            postProcessor = postProcessor
        )

        assertSame(asr, graph.asr())
        assertSame(tts, graph.tts())
        assertSame(translation, graph.translation())
        assertSame(sign, graph.sign())
        assertSame(postProcessor, graph.postProcessor())
    }

    @Test
    fun `build without a factory fails with typed MissingDependency`() {
        val failure = assertThrows(AiAndroidException.MissingDependency::class.java) {
            AiAndroidGraphBuilder().build()
        }

        assertEquals(AiAndroidGraphBuilder.ENGINE_FACTORY_DEPENDENCY, failure.dependency)
    }

    @Test
    fun `reading an unassembled stage fails with typed MissingDependency`() {
        val graph = AiAndroidGraph.builder()
            .factory(FakeEngineFactory())
            .asr(AsrConfig())
            .build()

        assertTrue(graph.has(EngineSlot.Asr))
        assertFalse(graph.has(EngineSlot.Sign))

        val failure = assertThrows(AiAndroidException.MissingDependency::class.java) { graph.sign() }
        assertEquals(EngineSlot.Sign.engineName, failure.dependency)
    }

    @Test
    fun `optional Koin module publishes every engine`() {
        val fakes = FakeEngineFactory()
        val graph = AiAndroidGraph.of(
            asr = fakes.asr,
            tts = fakes.tts,
            translation = fakes.translation,
            sign = fakes.sign,
            postProcessor = fakes.postProcessor
        )

        val koin = koinApplication { modules(aiAndroidModule(graph)) }.koin
        try {
            assertSame(graph, koin.get<AiAndroidEntryPoint>())
            assertSame(fakes.asr, koin.get<AsrEngine>())
            assertSame(fakes.tts, koin.get<TtsEngine>())
            assertSame(fakes.translation, koin.get<TranslationEngine>())
            assertSame(fakes.sign, koin.get<SignEngine>())
            assertSame(fakes.postProcessor, koin.get<PostProcessor>())
        } finally {
            koin.close()
        }
    }

    private companion object {
        const val CLASSIFIER_ID = "asl-fingerspelling"
    }
}
