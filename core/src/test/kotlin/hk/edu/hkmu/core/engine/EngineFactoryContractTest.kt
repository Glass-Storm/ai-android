package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Proves the DI contract is a plain, host-implementable interface: a hand-written object (no Hilt,
 * no Koin) satisfies [EngineFactory], and the engine contracts are SAM-convertible `fun interface`s.
 */
class EngineFactoryContractTest {

    @Test
    fun `host can implement EngineFactory without a DI framework`() {
        val factory = object : EngineFactory {
            override fun createAsrEngine(config: AsrConfig): AsrEngine = FakeAsrEngine(emptyList())

            override fun createTtsEngine(config: TtsConfig): TtsEngine = TtsEngine { emptyFlow() }

            override fun createTranslationEngine(config: TranslationConfig): TranslationEngine =
                TranslationEngine { emptyFlow() }

            override fun createSignEngine(config: SignConfig): SignEngine = SignEngine { emptyFlow() }

            override fun createPostProcessor(config: PostProcessConfig): PostProcessor = PostProcessor { emptyFlow() }
        }

        assertNotNull(factory.createAsrEngine(AsrConfig()))
        assertNotNull(factory.createTtsEngine(TtsConfig()))
        assertNotNull(factory.createTranslationEngine(TranslationConfig()))
        assertNotNull(factory.createSignEngine(SignConfig(classifierId = CLASSIFIER_ID)))
        assertNotNull(factory.createPostProcessor(PostProcessConfig()))
    }

    private companion object {
        const val CLASSIFIER_ID = "asl-fingerspelling"
    }
}
