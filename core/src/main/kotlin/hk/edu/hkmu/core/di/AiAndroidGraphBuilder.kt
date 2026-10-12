package hk.edu.hkmu.core.di

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.engine.EngineFactory
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Fluent builder that assembles an [AiAndroidGraph] from an [EngineFactory] and per-stage configs.
 *
 * A host supplies the factory (its own wiring) plus a config for each stage it wants, then calls
 * [build]. Stages without a config are left out of the graph, so a host can assemble a subset. The
 * factory itself is the one required dependency: [build] fails with
 * [AiAndroidException.MissingDependency] when it was never set.
 *
 * This is the no-container path — nothing here mentions Koin or Hilt, so a host with hand-rolled
 * dependencies can assemble every engine.
 */
class AiAndroidGraphBuilder {

    private var engineFactory: EngineFactory? = null
    private var asrConfig: AsrConfig? = null
    private var ttsConfig: TtsConfig? = null
    private var translationConfig: TranslationConfig? = null
    private var signConfig: SignConfig? = null
    private var postProcessConfig: PostProcessConfig? = null

    /** Sets the factory used to create engines. Required before [build]. */
    fun factory(factory: EngineFactory): AiAndroidGraphBuilder = apply { engineFactory = factory }

    /** Requests an ASR engine built from [config]. */
    fun asr(config: AsrConfig): AiAndroidGraphBuilder = apply { asrConfig = config }

    /** Requests a TTS engine built from [config]. */
    fun tts(config: TtsConfig): AiAndroidGraphBuilder = apply { ttsConfig = config }

    /** Requests a translation engine built from [config]. */
    fun translation(config: TranslationConfig): AiAndroidGraphBuilder = apply { translationConfig = config }

    /** Requests a sign engine built from [config]. */
    fun sign(config: SignConfig): AiAndroidGraphBuilder = apply { signConfig = config }

    /** Requests a recombination post-processor built from [config]. */
    fun postProcess(config: PostProcessConfig): AiAndroidGraphBuilder = apply { postProcessConfig = config }

    /**
     * Creates every configured engine through the factory and returns the assembled graph.
     *
     * @throws AiAndroidException.MissingDependency when no [factory] was set.
     */
    fun build(): AiAndroidGraph {
        val factory = engineFactory
            ?: throw AiAndroidException.MissingDependency(ENGINE_FACTORY_DEPENDENCY)
        return AiAndroidGraph.of(
            asr = asrConfig?.let { factory.createAsrEngine(it) },
            tts = ttsConfig?.let { factory.createTtsEngine(it) },
            translation = translationConfig?.let { factory.createTranslationEngine(it) },
            sign = signConfig?.let { factory.createSignEngine(it) },
            postProcessor = postProcessConfig?.let { factory.createPostProcessor(it) }
        )
    }

    companion object {
        /** Name reported by [AiAndroidException.MissingDependency] when no factory was set. */
        const val ENGINE_FACTORY_DEPENDENCY: String = "EngineFactory"
    }
}
