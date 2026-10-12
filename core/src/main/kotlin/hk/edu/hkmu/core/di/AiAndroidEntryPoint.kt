package hk.edu.hkmu.core.di

import hk.edu.hkmu.core.engine.AsrEngine
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.engine.TtsEngine
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Container-agnostic entry point that exposes the library's engines to a host.
 *
 * This is the seam that keeps the library DI-framework-neutral: it depends only on this contract,
 * never on Koin or Hilt. A host may consume [AiAndroidGraph] (the ready-made implementation), or
 * implement this interface itself with hand-rolled wiring, its own DI container, or a service
 * locator — all are equally valid.
 *
 * ### Partial graphs and missing stages
 * An implementation may be partial. Requesting an engine that was never assembled must fail with
 * [AiAndroidException.MissingDependency] instead of returning `null`, so a wiring mistake surfaces
 * as a typed error at first use rather than a `NullPointerException` later. Use `AiAndroidGraph.has`
 * to probe an optional stage without catching.
 */
interface AiAndroidEntryPoint {
    /**
     * The assembled speech-to-text engine.
     *
     * @throws AiAndroidException.MissingDependency when no ASR engine was assembled.
     */
    fun asr(): AsrEngine

    /**
     * The assembled text-to-speech engine.
     *
     * @throws AiAndroidException.MissingDependency when no TTS engine was assembled.
     */
    fun tts(): TtsEngine

    /**
     * The assembled translation engine.
     *
     * @throws AiAndroidException.MissingDependency when no translation engine was assembled.
     */
    fun translation(): TranslationEngine

    /**
     * The assembled sign-language engine.
     *
     * @throws AiAndroidException.MissingDependency when no sign engine was assembled.
     */
    fun sign(): SignEngine

    /**
     * The assembled recombination post-processor.
     *
     * @throws AiAndroidException.MissingDependency when no post-processor was assembled.
     */
    fun postProcessor(): PostProcessor
}
