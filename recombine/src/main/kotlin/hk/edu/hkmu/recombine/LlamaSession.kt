package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/** Stable identifier of the pinned recombination GGUF model. */
internal const val RECOMBINE_MODEL_ID: String = "Qwen/Qwen2.5-0.5B-Instruct-GGUF"

/**
 * Narrow internal seam over a loaded llama.cpp model + context.
 *
 * [LlamaPostProcessor] depends only on this interface, never on [NativeLlama] directly, so Tier A
 * (host JVM) tests substitute a fake and exercise the lazy-load/opt-out/streaming wiring without a
 * native library or model. The production implementation is [NativeLlamaSession], created by
 * [NativeLlamaSessionFactory], which validates the GGUF before touching native memory.
 */
internal interface LlamaSession : AutoCloseable {

    /**
     * Streams up to [maxTokens] whole-UTF-8 pieces through [onToken].
     *
     * @return pieces produced, or a negative native error code.
     */
    fun generate(prompt: String, maxTokens: Int, seed: Int, temperature: Float, onToken: (String) -> Unit): Int

    /** Frees the native model + context. Idempotent. */
    override fun close() {}
}

/** Production [LlamaSession] bound to a JNI handle returned by [NativeLlama.load]. */
internal class NativeLlamaSession(private val handle: Long) : LlamaSession {
    override fun generate(
        prompt: String,
        maxTokens: Int,
        seed: Int,
        temperature: Float,
        onToken: (String) -> Unit
    ): Int = NativeLlama.generate(handle, prompt, maxTokens, seed, temperature, TokenCallback(onToken))

    override fun close() = NativeLlama.free(handle)
}

/**
 * Creates a [NativeLlamaSession]: lazily loads the shared library, validates the GGUF magic,
 * initialises the ggml backend and loads the model + context on first use.
 *
 * @param libPath absolute `librecombine.so` path for host tests, or null for `System.loadLibrary`.
 */
internal class NativeLlamaSessionFactory(
    private val modelPath: String,
    private val contextWindowTokens: Int,
    private val libPath: String? = null
) {
    fun create(): LlamaSession {
        NativeLlama.ensureLoaded(libPath)
        verifyGguf(modelPath)
        NativeLlama.backendInit()
        val handle = NativeLlama.load(modelPath, contextWindowTokens)
        if (handle == 0L) {
            throw AiAndroidException.ModelLoadFailed(RECOMBINE_MODEL_ID)
        }
        return NativeLlamaSession(handle)
    }

    private fun verifyGguf(path: String) {
        val file = File(path)
        if (!file.isFile) {
            throw AiAndroidException.ModelAssetMissing(RECOMBINE_MODEL_ID, path)
        }
        val magic = ByteArray(GGUF_MAGIC_BYTES)
        val read = file.inputStream().use { it.read(magic) }
        if (read != GGUF_MAGIC_BYTES || String(magic, Charsets.US_ASCII) != GGUF_MAGIC) {
            throw AiAndroidException.ModelLoadFailed(
                RECOMBINE_MODEL_ID,
                IllegalArgumentException("not a GGUF file (bad magic): $path")
            )
        }
    }

    private companion object {
        const val GGUF_MAGIC = "GGUF"
        const val GGUF_MAGIC_BYTES = 4
    }
}
