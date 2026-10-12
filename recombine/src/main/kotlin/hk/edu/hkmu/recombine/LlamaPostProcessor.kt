package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * [PostProcessor] backed by the pinned llama.cpp + Qwen2.5-0.5B-Instruct GGUF, assembled as a
 * token stream (see [PostProcessor] for emission semantics).
 *
 * ### Lazy lifecycle (never eager)
 * The model is **not** loaded at construction. The native session is created on the first collected
 * [process] and released by [unload] (or [close]); after that the next [process] lazily reloads it.
 * [isLoaded] exposes residency without touching native memory.
 *
 * ### Opt-out
 * When [PostProcessConfig.recombinationEnabled] is false the LLM is bypassed entirely: the input
 * is emitted unchanged as a single token, preserving the streaming shape, and **no** model is ever
 * loaded.
 *
 * ### Determinism
 * [LlamaSamplingConfig.temperature] `<= 0` (the default) selects greedy decoding, which is
 * seed-independent and byte-reproducible — the Tier A/B test path. A positive temperature selects a
 * seeded temperature+dist sampler chain, reproducible for a fixed [LlamaSamplingConfig.seed].
 *
 * ### Backpressure & cancellation
 * The native token callback pushes through `callbackFlow` + `trySendBlocking`, so a slow collector
 * throttles generation. Cancelling collection closes the channel; the native loop stops on the next
 * failed send.
 */
class LlamaPostProcessor internal constructor(
    private val config: PostProcessConfig,
    private val sessionProvider: () -> LlamaSession,
    private val dispatcher: CoroutineDispatcher,
    private val sampling: LlamaSamplingConfig
) : PostProcessor, AutoCloseable {

    private val lock = Any()

    @Volatile
    private var session: LlamaSession? = null

    /** True once the native model + context are resident (loaded on the first [process] collection). */
    val isLoaded: Boolean get() = session != null

    override fun process(text: String): Flow<String> = callbackFlow {
        if (!config.recombinationEnabled) {
            // Opt-out: bypass the (never-loaded) model; stream the input unchanged.
            trySend(text)
            close()
            return@callbackFlow
        }
        requireWithinCap(text)
        val active = ensureSession()
        val prompt = RecombinationPrompt.build(text, config.language)
        val produced = active.generate(prompt, sampling.maxTokens, sampling.seed, sampling.temperature) { piece ->
            trySendBlocking(piece)
        }
        if (produced < 0) {
            close(IllegalStateException("native recombination failed (code=$produced)"))
        } else {
            close()
        }
    }.flowOn(dispatcher)

    /** Releases the native session if it was created; idempotent, then lazily reloads on next use. */
    fun unload() {
        val active = synchronized(lock) {
            session.also { session = null }
        }
        active?.close()
    }

    override fun close() = unload()

    private fun ensureSession(): LlamaSession = synchronized(lock) {
        session ?: sessionProvider().also { session = it }
    }

    private fun requireWithinCap(text: String) {
        if (text.length > config.maxInputChars) {
            throw AiAndroidException.InputTooLong(text.length, config.maxInputChars)
        }
    }

    companion object {
        /** Public entry point for a production engine over [modelFile] (a GGUF file). */
        fun create(
            config: PostProcessConfig = PostProcessConfig(),
            modelFile: File,
            libPath: String? = null,
            sampling: LlamaSamplingConfig = LlamaSamplingConfig()
        ): LlamaPostProcessor {
            val factory = NativeLlamaSessionFactory(
                modelPath = modelFile.absolutePath,
                contextWindowTokens = config.runtime.contextWindowTokens,
                libPath = libPath
            )
            return LlamaPostProcessor(config, factory::create, Dispatchers.Default, sampling)
        }

        /** Test seam: builds an engine over an injected session provider (no native). */
        internal fun forTesting(
            config: PostProcessConfig,
            sessionProvider: () -> LlamaSession,
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
            sampling: LlamaSamplingConfig = LlamaSamplingConfig()
        ): LlamaPostProcessor = LlamaPostProcessor(config, sessionProvider, dispatcher, sampling)
    }
}
