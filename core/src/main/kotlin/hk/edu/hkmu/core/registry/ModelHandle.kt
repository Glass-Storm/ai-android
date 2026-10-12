package hk.edu.hkmu.core.registry

/**
 * Owns the native/global resources of one instantiated model.
 *
 * The registry owns the handle for the whole residency of the model and calls [close] at most once,
 * when the model is actually unloaded — either explicitly through [ModelRegistry.unload], or by LRU
 * eviction / memory-trim unloading. Callers never close a handle directly; they hold a
 * [ModelLease] instead, which keeps the model in use.
 *
 * Implementations must tolerate [close] being called on an already-freed handle and must release
 * every native context/weight they created so an evicted model does not leak memory.
 */
interface ModelHandle {
    /** Frees the underlying native resources. Called exactly once per residency. */
    fun close()
}

/**
 * Instantiates a model after the license gate has passed.
 *
 * This is the single extension seam the registry needs, which keeps it generic (over `H`) and free
 * of any dependency on the `models` module or a concrete engine. [ModelRegistry] invokes [load]
 * only after the injected [hk.edu.hkmu.core.license.LicensePolicy] permits the model, and invokes
 * it at most once per resident model.
 *
 * Implementations surface a [hk.edu.hkmu.core.error.AiAndroidException] on failure — e.g.
 * `ModelAssetMissing` when a required asset is absent, or `ModelLoadFailed` when the native runtime
 * refuses the model. Any other throwable is wrapped by the registry into `ModelLoadFailed`.
 */
fun interface ModelLoader<H : ModelHandle> {
    /** Instantiates [registration] and returns a handle that owns its resources. */
    fun load(registration: ModelRegistration): H
}
