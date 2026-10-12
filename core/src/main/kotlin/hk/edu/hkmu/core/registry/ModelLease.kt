package hk.edu.hkmu.core.registry

/**
 * A pinned reference to a resident model, returned by [ModelRegistry.acquire].
 *
 * Holding a lease marks the model **in use**: it is never selected by LRU eviction and never
 * unloaded by [ModelRegistry.onTrimMemory] while any lease is open. The shared weights behind the
 * handle survive any number of concurrent leases; the registry frees them only once the last lease
 * is released and the model is later evicted or unloaded.
 *
 * [close] is idempotent — calling it more than once releases the reference at most once — so it is
 * safe to use in a `use { }` block. Callers must not close [handle] directly; the registry owns the
 * handle's lifetime.
 *
 * @property modelId Identifier of the leased model.
 * @property handle The instantiated model handle, shared across all of this model's leases.
 */
class ModelLease<H : ModelHandle> internal constructor(
    val modelId: String,
    val handle: H,
    private val onClose: (ModelLease<H>) -> Unit
) : AutoCloseable {

    private var closed: Boolean = false

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        onClose(this)
    }
}
