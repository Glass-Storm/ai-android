package hk.edu.hkmu.core.di

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.EngineFactory
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.engine.TtsEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Optional Koin definitions for an [AiAndroidEntryPoint].
 *
 * Koin is a **convenience, never a requirement**. This file is the only place the library references
 * Koin; it compiles against a `compileOnly` dependency, so Koin stays off `:core`'s runtime classpath
 * and out of its published metadata. A host that never calls [aiAndroidModule] never loads these
 * classes, and a host using manual DI or Hilt consumes [AiAndroidGraph] / [AiAndroidEntryPoint]
 * directly instead. A Koin host must add `io.insert-koin:koin-core` to its own classpath.
 *
 * Every engine is registered lazily from the [entryPoint]; a stage that was not assembled surfaces as
 * [hk.edu.hkmu.core.error.AiAndroidException.MissingDependency] at resolution time, the same typed
 * failure the container-free path raises.
 *
 * @param entryPoint The graph (or any host implementation) whose engines should be published.
 */
fun aiAndroidModule(entryPoint: AiAndroidEntryPoint): Module = module {
    single { entryPoint }
    single<AsrEngine> { entryPoint.asr() }
    single<TtsEngine> { entryPoint.tts() }
    single<TranslationEngine> { entryPoint.translation() }
    single<SignEngine> { entryPoint.sign() }
    single<PostProcessor> { entryPoint.postProcessor() }
}

/**
 * Convenience overload that first assembles a graph through [factory] (applying [configure]) and then
 * publishes it via [aiAndroidModule].
 *
 * Nothing is created until a definition is resolved, so a partial [configure] that omits a stage
 * simply leaves that engine unregistered.
 */
fun aiAndroidModule(factory: EngineFactory, configure: AiAndroidGraphBuilder.() -> Unit = {}): Module {
    val builder = AiAndroidGraphBuilder().factory(factory)
    builder.configure()
    return aiAndroidModule(builder.build())
}
