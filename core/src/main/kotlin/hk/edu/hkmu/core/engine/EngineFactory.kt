package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig

/**
 * Host-implementable entry point that creates configured engines.
 *
 * The library never instantiates engines itself and never requires a DI framework: a host may
 * implement this interface by hand, with Koin, or with Hilt — all are equally valid. Implementors
 * own model acquisition and loading policy, and must route license checks through the
 * `LicensePolicy` interface before constructing an engine.
 */
interface EngineFactory {
    /** Creates an ASR engine for [config]. */
    fun createAsrEngine(config: AsrConfig): AsrEngine

    /** Creates a TTS engine for [config]. */
    fun createTtsEngine(config: TtsConfig): TtsEngine

    /** Creates a translation engine for [config]. */
    fun createTranslationEngine(config: TranslationConfig): TranslationEngine

    /** Creates a sign engine for [config]. */
    fun createSignEngine(config: SignConfig): SignEngine

    /** Creates a recombination post-processor for [config]. */
    fun createPostProcessor(config: PostProcessConfig): PostProcessor
}
