package hk.edu.hkmu.fixture

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
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import hk.edu.hkmu.testkit.license.FakeLicensePolicy

/**
 * Wires the library's **public API only**, entirely from test-kit fakes.
 *
 * Nothing here reaches into an `internal` declaration or a binding class: every type is part of the
 * published surface (`hk.edu.hkmu.core.*`) or the test kit (`hk.edu.hkmu.testkit.*`). That makes
 * this object the executable form of the API-boundary check.
 */
object FixtureApiGraph {

    private val modelLoader: ModelLoader<FixtureModelHandle> =
        ModelLoader { registration -> FixtureModelHandle(registration.modelId) }

    /** Builds a fully wired graph with a fake behind every public contract. */
    fun wire(): FixtureWiring {
        val factory: EngineFactory = FakeEngineFactory()
        val policy: LicensePolicy = FakeLicensePolicy()
        val registry = ModelRegistry(
            loader = modelLoader,
            licensePolicy = policy,
            intent = UsageIntent.SelfUse
        )
        return FixtureWiring(
            factory = factory,
            licensePolicy = policy,
            asr = factory.createAsrEngine(AsrConfig()),
            tts = factory.createTtsEngine(TtsConfig()),
            translation = factory.createTranslationEngine(TranslationConfig()),
            sign = factory.createSignEngine(SignConfig(classifierId = "asl")),
            postProcessor = factory.createPostProcessor(PostProcessConfig()),
            registry = registry
        )
    }
}

/**
 * Every public engine contract plus the license policy and model registry, resolved from fakes.
 *
 * @property factory The fake engine factory the engines were created from.
 * @property licensePolicy The fake license policy injected into the registry.
 * @property asr Public ASR contract.
 * @property tts Public TTS contract.
 * @property translation Public translation contract.
 * @property sign Public sign-recognition contract.
 * @property postProcessor Public recombination contract.
 * @property registry Public model-lifecycle registry.
 */
@Suppress("LongParameterList")
class FixtureWiring(
    val factory: EngineFactory,
    val licensePolicy: LicensePolicy,
    val asr: AsrEngine,
    val tts: TtsEngine,
    val translation: TranslationEngine,
    val sign: SignEngine,
    val postProcessor: PostProcessor,
    val registry: ModelRegistry<FixtureModelHandle>
)
