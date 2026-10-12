package hk.edu.hkmu.core.registry

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Handle that records how many times it was freed. */
private class FakeHandle : ModelHandle {
    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount += 1
    }
}

/** Loader that records which registrations were instantiated and the handles it produced. */
private class RecordingLoader : ModelLoader<FakeHandle> {
    val loaded: MutableList<String> = mutableListOf()
    val handles: MutableList<FakeHandle> = mutableListOf()

    override fun load(registration: ModelRegistration): FakeHandle {
        loaded += registration.modelId
        val handle = FakeHandle()
        handles += handle
        return handle
    }
}

/** Policy mirroring the documented `LicensePolicy` surface for the registry gate tests. */
private class DocumentedPolicy : LicensePolicy {
    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision = when (intent) {
        UsageIntent.SelfUse -> LicenseDecision.Allowed(license.notice)
        UsageIntent.Commercial -> when (license.kind) {
            LicenseKind.Permissive -> LicenseDecision.Allowed(license.notice)
            else -> LicenseDecision.Restricted("blocked ${license.kind.name}", license, intent)
        }
    }
}

/**
 * Locks the registry lifecycle contract: refcount pins shared weights against eviction and trim,
 * LRU evicts the coldest **idle** model, and the license gate runs **before** any instantiation.
 */
class ModelRegistryTest {

    @Test
    fun `trim critical evicts cold idle model and retains in-use model`() {
        val loader = RecordingLoader()
        val registry = registry(loader)
        registerAll(registry)
        registry.load(A)
        registry.load(B)
        val leaseA = registry.acquire(A)
        assertEquals(1, registry.refCount(A))

        val unloaded = registry.onTrimMemory(TrimLevel.RunningCritical)

        assertEquals(listOf(B), unloaded)
        assertTrue("in-use A is retained", registry.isResident(A))
        assertFalse("cold idle B is evicted", registry.isResident(B))
        leaseA.close()
    }

    @Test
    fun `unloading a refcounted model fails with ModelInUse`() {
        val loader = RecordingLoader()
        val registry = registry(loader)
        registry.register(ModelRegistration(A, PERMISSIVE))
        val lease = registry.acquire(A)

        val error = assertThrows(AiAndroidException.ModelInUse::class.java) { registry.unload(A) }

        assertEquals(A, error.modelId)
        assertTrue("in-use model stays resident", registry.isResident(A))
        lease.close()
        registry.unload(A)
        assertFalse("idle model can now be unloaded", registry.isResident(A))
    }

    @Test
    fun `lru evicts the coldest idle model over capacity`() {
        val loader = RecordingLoader()
        val registry = registry(loader)
        registerAll(registry)
        registry.load(A)
        registry.load(B)
        registry.acquire(A).close()

        registry.load(C)

        assertTrue("recently used A is retained", registry.isResident(A))
        assertFalse("coldest idle B is evicted", registry.isResident(B))
        assertTrue("new model C is resident", registry.isResident(C))
    }

    @Test
    fun `commercial policy refuses non-commercial model before instantiation`() {
        val loader = RecordingLoader()
        val registry = ModelRegistry(loader, DocumentedPolicy(), UsageIntent.Commercial, CAPACITY_TWO)
        registry.register(ModelRegistration(NLLB, NON_COMMERCIAL))

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) { registry.load(NLLB) }

        assertEquals(NLLB, error.modelId)
        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals(UsageIntent.Commercial, error.intent)
        assertEquals("loader must never be invoked", 0, loader.loaded.size)
        assertFalse("restricted model is not resident", registry.isResident(NLLB))
    }

    @Test
    fun `self-use permits and loads a non-commercial model after the gate`() {
        val loader = RecordingLoader()
        val registry = ModelRegistry(loader, DocumentedPolicy(), UsageIntent.SelfUse, CAPACITY_TWO)
        registry.register(ModelRegistration(NLLB, NON_COMMERCIAL))

        val lease = registry.acquire(NLLB)

        assertEquals(listOf(NLLB), loader.loaded)
        assertEquals(1, registry.refCount(NLLB))
        lease.close()
        assertEquals(0, registry.refCount(NLLB))
    }

    @Test
    fun `concurrent leases share one handle and release refcounts`() {
        val loader = RecordingLoader()
        val registry = registry(loader)
        registry.register(ModelRegistration(A, PERMISSIVE))

        val first = registry.acquire(A)
        val second = registry.acquire(A)

        assertSame("shared weights behind one handle", first.handle, second.handle)
        assertEquals("instantiated once", 1, loader.loaded.size)
        assertEquals(2, registry.refCount(A))
        first.close()
        assertEquals(1, registry.refCount(A))
        second.close()
        assertEquals(0, registry.refCount(A))
        assertTrue("still cached while idle", registry.isResident(A))
    }

    @Test
    fun `closing a lease twice releases one reference`() {
        val registry = registry(RecordingLoader())
        registry.register(ModelRegistration(A, PERMISSIVE))
        val lease = registry.acquire(A)

        lease.close()
        lease.close()

        assertEquals(0, registry.refCount(A))
    }

    @Test
    fun `evicted model handle is closed exactly once`() {
        val loader = RecordingLoader()
        val registry = registry(loader)
        registerAll(registry)
        registry.load(A)
        val handleA = loader.handles.single()
        registry.load(B)

        registry.load(C)

        assertFalse(registry.isResident(A))
        assertEquals(1, handleA.closeCount)
    }

    @Test
    fun `trim low keeps the most recently used idle model`() {
        val registry = registry(RecordingLoader())
        registerAll(registry)
        registry.load(A)
        registry.load(B)

        val unloaded = registry.onTrimMemory(TrimLevel.RunningLow)

        assertEquals(listOf(A), unloaded)
        assertTrue(registry.isResident(B))
        assertFalse(registry.isResident(A))
    }

    @Test
    fun `component callbacks levels map to trim levels`() {
        assertEquals(TrimLevel.RunningModerate, TrimLevel.fromComponentCallbacks(COMPONENT_RUNNING_MODERATE))
        assertEquals(TrimLevel.RunningLow, TrimLevel.fromComponentCallbacks(COMPONENT_RUNNING_LOW))
        assertEquals(TrimLevel.RunningCritical, TrimLevel.fromComponentCallbacks(COMPONENT_RUNNING_CRITICAL))
        assertEquals(TrimLevel.UiHidden, TrimLevel.fromComponentCallbacks(COMPONENT_UI_HIDDEN))
        assertEquals(TrimLevel.Background, TrimLevel.fromComponentCallbacks(COMPONENT_BACKGROUND))
        assertEquals(TrimLevel.Complete, TrimLevel.fromComponentCallbacks(COMPONENT_COMPLETE))
        assertEquals(TrimLevel.RunningModerate, TrimLevel.fromComponentCallbacks(COMPONENT_UNKNOWN))
    }

    @Test
    fun `unknown model id fails with ModelNotAvailable`() {
        val registry = registry(RecordingLoader())
        assertThrows(AiAndroidException.ModelNotAvailable::class.java) { registry.load(GHOST) }
        assertThrows(AiAndroidException.ModelNotAvailable::class.java) { registry.unload(GHOST) }
    }

    @Test
    fun `loader failure surfaces as ModelLoadFailed`() {
        val failure = IllegalStateException("boom")
        val registry = ModelRegistry(
            ModelLoader { throw failure },
            DocumentedPolicy(),
            UsageIntent.SelfUse,
            CAPACITY_TWO
        )
        registry.register(ModelRegistration(A, PERMISSIVE))

        val error = assertThrows(AiAndroidException.ModelLoadFailed::class.java) { registry.load(A) }

        assertSame(failure, error.cause)
        assertFalse(registry.isResident(A))
    }

    @Test
    fun `typed loader error is preserved`() {
        val registry = ModelRegistry(
            ModelLoader<FakeHandle> { throw AiAndroidException.ModelAssetMissing(it.modelId, ASSET) },
            DocumentedPolicy(),
            UsageIntent.SelfUse,
            CAPACITY_TWO
        )
        registry.register(ModelRegistration(A, PERMISSIVE))

        assertThrows(AiAndroidException.ModelAssetMissing::class.java) { registry.load(A) }
    }

    private fun registry(loader: RecordingLoader): ModelRegistry<FakeHandle> =
        ModelRegistry(loader, DocumentedPolicy(), UsageIntent.SelfUse, CAPACITY_TWO)

    private fun registerAll(registry: ModelRegistry<FakeHandle>) {
        registry.register(ModelRegistration(A, PERMISSIVE))
        registry.register(ModelRegistration(B, PERMISSIVE))
        registry.register(ModelRegistration(C, PERMISSIVE))
    }

    private companion object {
        const val A = "model-a"
        const val B = "model-b"
        const val C = "model-c"
        const val GHOST = "model-ghost"
        const val NLLB = "nllb-200-distilled-600m"
        const val ASSET = "model.onnx"
        const val CAPACITY_TWO = 2

        const val COMPONENT_RUNNING_MODERATE = 5
        const val COMPONENT_RUNNING_LOW = 10
        const val COMPONENT_RUNNING_CRITICAL = 15
        const val COMPONENT_UI_HIDDEN = 20
        const val COMPONENT_BACKGROUND = 40
        const val COMPONENT_COMPLETE = 80
        const val COMPONENT_UNKNOWN = -9999

        val PERMISSIVE: ModelLicense = ModelLicense(LicenseKind.Permissive, spdxId = "Apache-2.0")
        val NON_COMMERCIAL: ModelLicense = ModelLicense(LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
    }
}
