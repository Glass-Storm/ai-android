package hk.edu.hkmu.testkit.engine

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.EngineFactory
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.engine.TtsEngine

/**
 * In-memory [EngineFactory] that hands back injectable fakes and records the configuration each
 * `create*` call received.
 *
 * This is the seam that proves fakes are injectable everywhere: a consumer builds a single
 * [FakeEngineFactory], substitutes any engine with a purpose-built fake, and passes the factory
 * through the library's public API without the library ever knowing it is a test double.
 *
 * @property asr Engine returned by [createAsrEngine].
 * @property tts Engine returned by [createTtsEngine].
 * @property translation Engine returned by [createTranslationEngine].
 * @property sign Engine returned by [createSignEngine].
 * @property postProcessor Engine returned by [createPostProcessor].
 */
class FakeEngineFactory(
    val asr: AsrEngine = FakeAsrEngine(),
    val tts: TtsEngine = FakeTtsEngine(),
    val translation: TranslationEngine = FakeTranslationEngine(),
    val sign: SignEngine = FakeSignEngine(),
    val postProcessor: PostProcessor = FakePostProcessor()
) : EngineFactory {

    /** Configurations passed to [createAsrEngine], in order. */
    val asrConfigs: MutableList<AsrConfig> = mutableListOf()

    /** Configurations passed to [createTtsEngine], in order. */
    val ttsConfigs: MutableList<TtsConfig> = mutableListOf()

    /** Configurations passed to [createTranslationEngine], in order. */
    val translationConfigs: MutableList<TranslationConfig> = mutableListOf()

    /** Configurations passed to [createSignEngine], in order. */
    val signConfigs: MutableList<SignConfig> = mutableListOf()

    /** Configurations passed to [createPostProcessor], in order. */
    val postProcessConfigs: MutableList<PostProcessConfig> = mutableListOf()

    override fun createAsrEngine(config: AsrConfig): AsrEngine {
        asrConfigs += config
        return asr
    }

    override fun createTtsEngine(config: TtsConfig): TtsEngine {
        ttsConfigs += config
        return tts
    }

    override fun createTranslationEngine(config: TranslationConfig): TranslationEngine {
        translationConfigs += config
        return translation
    }

    override fun createSignEngine(config: SignConfig): SignEngine {
        signConfigs += config
        return sign
    }

    override fun createPostProcessor(config: PostProcessConfig): PostProcessor {
        postProcessConfigs += config
        return postProcessor
    }
}
