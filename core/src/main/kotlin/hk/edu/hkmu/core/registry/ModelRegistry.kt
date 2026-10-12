package hk.edu.hkmu.core.registry

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Mutable per-model lifecycle state. File-private; owned exclusively by [ModelRegistry]. */
private class Entry<H : ModelHandle>(var registration: ModelRegistration) {
    var handle: H? = null
    var refCount: Int = 0
    var lastUsed: Long = 0L

    /** True once a handle exists (the model is resident, whether or not it is pinned). */
    val resident: Boolean get() = handle != null

    /** True while at least one [ModelLease] is outstanding; a pinned model is never evicted. */
    val pinned: Boolean get() = refCount > 0

    fun touch(counter: Long) {
        lastUsed = counter
    }

    /** Frees the handle exactly once and marks the entry non-resident. */
    fun evict() {
        val owned = handle ?: return
        handle = null
        owned.close()
    }
}

/** Evicts coldest idle models until the resident count fits [capacity]. */
private fun <H : ModelHandle> evictOverCapacity(entries: Collection<Entry<H>>, capacity: Int) {
    while (entries.count { it.resident } > capacity) {
        val victim = entries.filter { it.resident && !it.pinned }.minByOrNull { it.lastUsed } ?: return
        victim.evict()
    }
}

/**
 * Thread-safe registry of model lifecycles: registration, reference-counted acquisition, LRU
 * eviction, caller-forwarded memory-trim unloading, and a license gate.
 *
 * ### Lifecycle
 * - [register] records a [ModelRegistration] (metadata only — nothing is instantiated).
 * - [load] makes a model **resident** but unpinned (cache warming); it may be evicted at any time.
 * - [acquire] makes a model resident **and pinned** via a [ModelLease]; a pinned model is never
 *   evicted nor trimmed. Multiple concurrent leases share the one underlying handle.
 * - [release] (or closing a lease) drops one reference; the model becomes idle once the count
 *   reaches zero but stays resident and evictable.
 * - [unload] force-frees an idle model; if it is still pinned it fails with
 *   [AiAndroidException.ModelInUse].
 *
 * ### Capacity and LRU
 * When yielding a new residency would exceed [capacity], the coldest **idle** (unpinned) model is
 * evicted first. If every resident model is pinned, the registry temporarily exceeds [capacity]
 * rather than evict an in-use model; the overage is resolved on the next load or trim.
 *
 * ### `onTrimMemory` caller contract
 * The library owns no Android lifecycle, so it never receives trim callbacks itself. A host that
 * owns one forwards `ComponentCallbacks2.onTrimMemory(level)` into [onTrimMemory], mapping the raw
 * integer through [TrimLevel.fromComponentCallbacks]. Only idle models are ever unloaded.
 *
 * ### License gate
 * Before any model is instantiated, [acquire]/[load] call the injected [LicensePolicy] with the
 * model's [hk.edu.hkmu.core.license.ModelLicense] and this registry's [intent]. A
 * [LicenseDecision.Restricted] verdict throws [AiAndroidException.LicenseRestricted] and the
 * [ModelLoader] is **never** invoked, so a non-commercial model can never be brought into memory
 * under the wrong intent.
 *
 * All mutating operations are serialized on an internal lock, so a shared native handle cannot be
 * loaded or freed twice under concurrency.
 *
 * @param loader Instantiates a model once the license gate has passed.
 * @param licensePolicy License gate consulted before instantiation.
 * @param intent How the host intends to use models; drives the license decision.
 * @param capacity Maximum number of simultaneously resident models.
 */
class ModelRegistry<H : ModelHandle>(
    private val loader: ModelLoader<H>,
    private val licensePolicy: LicensePolicy,
    private val intent: UsageIntent = UsageIntent.SelfUse,
    private val capacity: Int = DEFAULT_CAPACITY
) {
    private val lock = ReentrantLock()
    private val entries = LinkedHashMap<String, Entry<H>>()
    private var useCounter: Long = 0L

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** Records [registration] under its `modelId`, replacing a prior descriptor for the same id. */
    fun register(registration: ModelRegistration) {
        lock.withLock {
            val existing = entries[registration.modelId]
            if (existing == null) {
                entries[registration.modelId] = Entry(registration)
            } else {
                existing.registration = registration
            }
        }
    }

    /** Makes [modelId] resident and idle (cache warm-up). It may be evicted at any time. */
    fun load(modelId: String) {
        lock.withLock { ensureResident(modelId) }
    }

    /**
     * Makes [modelId] resident and pinned, returning a [ModelLease] the caller must close.
     *
     * @throws AiAndroidException.ModelNotAvailable if [modelId] was never registered.
     * @throws AiAndroidException.LicenseRestricted if the license gate refuses the model.
     * @throws AiAndroidException.ModelLoadFailed if the loader throws a non-typed failure.
     */
    fun acquire(modelId: String): ModelLease<H> = lock.withLock {
        val entry = ensureResident(modelId)
        entry.refCount += 1
        ModelLease(modelId, requireNotNull(entry.handle), ::release)
    }

    /** Drops one reference held through [lease]. Idempotent per lease. */
    fun release(lease: ModelLease<H>) {
        lock.withLock {
            val entry = entries[lease.modelId]
            if (entry != null && entry.refCount > 0) {
                entry.refCount -= 1
            }
        }
    }

    /**
     * Force-frees an idle resident model and closes its handle.
     *
     * @throws AiAndroidException.ModelNotAvailable if [modelId] was never registered.
     * @throws AiAndroidException.ModelInUse if the model is still pinned by an open lease.
     */
    fun unload(modelId: String) {
        lock.withLock {
            val entry = entries[modelId] ?: throw AiAndroidException.ModelNotAvailable(modelId)
            if (entry.pinned) {
                throw AiAndroidException.ModelInUse(modelId)
            }
            entry.evict()
        }
    }

    /**
     * Unloads idle models in response to caller-forwarded memory pressure.
     *
     * See [TrimLevel] for the per-level policy. Returns the ids that were unloaded, in unload order.
     */
    fun onTrimMemory(level: TrimLevel): List<String> = lock.withLock {
        val idle = entries.values.filter { it.resident && !it.pinned }
        val victims = when (level) {
            TrimLevel.Complete, TrimLevel.RunningCritical -> idle
            TrimLevel.RunningLow, TrimLevel.Background -> idle.sortedByDescending { it.lastUsed }.drop(1)
            TrimLevel.UiHidden, TrimLevel.RunningModerate -> emptyList()
        }
        victims.forEach { it.evict() }
        victims.map { it.registration.modelId }
    }

    /** True when [modelId] has a live handle (resident, pinned or idle). */
    fun isResident(modelId: String): Boolean = lock.withLock { entries[modelId]?.resident == true }

    /** Number of outstanding leases for [modelId] (0 when idle or absent). */
    fun refCount(modelId: String): Int = lock.withLock { entries[modelId]?.refCount ?: 0 }

    @Suppress("TooGenericExceptionCaught")
    private fun ensureResident(modelId: String): Entry<H> {
        val entry = entries[modelId] ?: throw AiAndroidException.ModelNotAvailable(modelId)
        if (entry.resident) {
            entry.touch(++useCounter)
            return entry
        }
        gateLicense(entry)
        entry.handle = try {
            loader.load(entry.registration)
        } catch (e: Exception) {
            throw e as? AiAndroidException ?: AiAndroidException.ModelLoadFailed(modelId, e)
        }
        entry.touch(++useCounter)
        evictOverCapacity(entries.values, capacity)
        return entry
    }

    private fun gateLicense(entry: Entry<H>) {
        val license = entry.registration.license
        val decision = licensePolicy.evaluate(license, intent)
        if (decision is LicenseDecision.Restricted) {
            throw AiAndroidException.LicenseRestricted(entry.registration.modelId, license.kind, intent)
        }
    }

    companion object {
        /** Default maximum number of simultaneously resident models. */
        const val DEFAULT_CAPACITY = 4
    }
}
