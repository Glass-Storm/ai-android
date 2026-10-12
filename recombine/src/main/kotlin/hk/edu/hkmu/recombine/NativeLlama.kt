package hk.edu.hkmu.recombine

import java.util.concurrent.atomic.AtomicBoolean

/** Streaming sink for decoded tokens. Called once per whole-UTF-8 piece. */
fun interface TokenCallback {
    fun onToken(token: String)
}

/**
 * Thin JNI surface over the locally-built llama.cpp shared library (`librecombine.so`).
 *
 * The native library is loaded lazily on the first [ensureLoaded] call — never from a class
 * initialiser — so merely referencing this object costs nothing until recombination actually
 * runs. All model/context lifetime is owned by [LlamaPostProcessor], which keeps an opaque
 * `0` handle until its first `process()`.
 */
object NativeLlama {

    /** Library name resolved from the packaged `jniLibs` (arm64-v8a / x86_64). */
    const val LIBRARY_NAME: String = "recombine"

    private val libLoaded = AtomicBoolean(false)

    /**
     * Loads the native library exactly once.
     *
     * @param explicitPath absolute path to `librecombine.so` for host tests, or null to use
     *   `System.loadLibrary(LIBRARY_NAME)` so Android packaging resolves it from `jniLibs`.
     */
    @Synchronized
    fun ensureLoaded(explicitPath: String? = null) {
        if (libLoaded.get()) return
        if (explicitPath != null) {
            System.load(explicitPath)
        } else {
            System.loadLibrary(LIBRARY_NAME)
        }
        libLoaded.set(true)
    }

    /** True once [ensureLoaded] has successfully loaded the shared library. */
    val isLibraryLoaded: Boolean get() = libLoaded.get()

    /** Initialises the ggml backends exactly once; call before [load]. */
    @JvmStatic external fun backendInit()

    /** Releases ggml backends. */
    @JvmStatic external fun backendFree()

    /**
     * Loads a GGUF model plus a fresh context sized for [contextWindowTokens].
     *
     * @return an opaque handle `> 0` on success, `0` on load failure; the caller maps `0` to
     *   `AiAndroidException.ModelLoadFailed`.
     */
    @JvmStatic external fun load(modelPath: String, contextWindowTokens: Int): Long

    /**
     * Streams up to [maxTokens] pieces to [callback]. A [temperature] `<= 0` selects
     * deterministic greedy decoding; a positive value selects a seeded temperature+dist sampler
     * chain ([seed] makes it reproducible).
     *
     * @return pieces produced, or a negative native error code.
     */
    @Suppress("LongParameterList") // JNI signature is fixed by the native binding.
    @JvmStatic
    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        seed: Int,
        temperature: Float,
        callback: TokenCallback
    ): Int

    /** Frees the model + context behind [handle]. Safe to call with `0`. */
    @JvmStatic external fun free(handle: Long)
}
