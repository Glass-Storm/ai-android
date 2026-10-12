package hk.edu.hkmu.core.di

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.EngineFactory
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.engine.TtsEngine
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Immutable, DI-framework-agnostic facade over a set of assembled engines.
 *
 * The graph is a plain value: it holds whatever engines the host supplied and never reaches into a
 * container. It can be produced three equivalent ways — [AiAndroidGraphBuilder] (assemble through an
 * [EngineFactory]), [of] (a host that already owns engine instances), or the optional Koin module in
 * `AiAndroidModule.kt` — and every path yields the same [AiAndroidEntryPoint] contract, so downstream
 * code never depends on the container that built it.
 *
 * ### Partial graphs
 * A graph need not be complete. A stage that was not assembled is simply absent; reading it through
 * [asr], [tts], [translation], [sign], or [postProcessor] fails fast with
 * [AiAndroidException.MissingDependency] naming the missing engine, while [has] lets a caller probe
 * an optional stage without catching.
 *
 * @property engines Engines keyed by stage; an absent stage is simply not present.
 */
class AiAndroidGraph internal constructor(
    private val engines: Map<EngineSlot, Any>
) : AiAndroidEntryPoint {

    override fun asr(): AsrEngine = engine(EngineSlot.Asr, AsrEngine::class.java)

    override fun tts(): TtsEngine = engine(EngineSlot.Tts, TtsEngine::class.java)

    override fun translation(): TranslationEngine = engine(EngineSlot.Translation, TranslationEngine::class.java)

    override fun sign(): SignEngine = engine(EngineSlot.Sign, SignEngine::class.java)

    override fun postProcessor(): PostProcessor = engine(EngineSlot.PostProcess, PostProcessor::class.java)

    /** True when the engine for [slot] was assembled into this graph. */
    fun has(slot: EngineSlot): Boolean = engines.containsKey(slot)

    /**
     * Returns the engine occupying [slot] cast to [type].
     *
     * @throws AiAndroidException.MissingDependency when [slot] was not assembled.
     */
    fun <T : Any> engine(slot: EngineSlot, type: Class<T>): T {
        val engine = engines[slot] ?: throw AiAndroidException.MissingDependency(slot.engineName)
        return type.cast(engine)
    }

    companion object {
        /** A new [AiAndroidGraphBuilder] for assembling the graph through an `EngineFactory`. */
        @JvmStatic
        fun builder(): AiAndroidGraphBuilder = AiAndroidGraphBuilder()

        /**
         * Builds a graph directly from already-constructed engine instances.
         *
         * Use this from a host that owns its engines and wants no factory indirection. Omit any
         * stage to leave it unassembled.
         */
        @JvmStatic
        fun of(
            asr: AsrEngine? = null,
            tts: TtsEngine? = null,
            translation: TranslationEngine? = null,
            sign: SignEngine? = null,
            postProcessor: PostProcessor? = null
        ): AiAndroidGraph {
            val engines = mutableMapOf<EngineSlot, Any>()
            asr?.let { engines[EngineSlot.Asr] = it }
            tts?.let { engines[EngineSlot.Tts] = it }
            translation?.let { engines[EngineSlot.Translation] = it }
            sign?.let { engines[EngineSlot.Sign] = it }
            postProcessor?.let { engines[EngineSlot.PostProcess] = it }
            return AiAndroidGraph(engines)
        }
    }
}
