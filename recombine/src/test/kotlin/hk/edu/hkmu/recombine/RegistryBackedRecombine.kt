package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelHandle
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import kotlinx.coroutines.Dispatchers

/**
 * Tier A fixture that makes the recombination GGUF's residency observable **through the shared
 * [ModelRegistry]** (todo 9) without loading any native memory.
 *
 * The [LlamaPostProcessor]'s session-provider seam is wired to `registry.acquire`; the loader creates
 * the fake session, so the GGUF becomes resident exactly when the post-processor's first `process()`
 * calls the provider — and only then. Closing the session releases the lease and unloads the model,
 * so `unload()` frees it and the next `process()` lazily reloads. This is the "recombine disabled ⇒
 * one fewer GGUF resident" observable used by [LazyRecombinationResidencyTest].
 */
internal class RegistryBackedRecombine(recombinationEnabled: Boolean) {

    /** Every fake native session the registry loader created, in creation order. */
    val createdSessions: MutableList<FakeLlamaSession> = mutableListOf()

    private val registry = ModelRegistry<SessionHandle>(
        loader = { SessionHandle(FakeLlamaSession().also { createdSessions += it }) },
        licensePolicy = DefaultLicensePolicy(),
        intent = UsageIntent.SelfUse,
        capacity = 1
    )

    /** The post-processor under test, wired to the registry-backed provider. */
    val processor: LlamaPostProcessor

    init {
        registry.register(ModelRegistration(RECOMBINE_MODEL_ID, QWEN_LICENSE))
        processor = LlamaPostProcessor.forTesting(
            PostProcessConfig(recombinationEnabled = recombinationEnabled),
            ::acquireSession,
            Dispatchers.Unconfined
        )
    }

    /** True while the recombination GGUF has a live handle in the registry. */
    fun resident(): Boolean = registry.isResident(RECOMBINE_MODEL_ID)

    /** Number of resident recombination GGUFs (0 or 1) — the budget observable. */
    fun residentCount(): Int = if (resident()) 1 else 0

    private fun acquireSession(): LlamaSession {
        val lease = registry.acquire(RECOMBINE_MODEL_ID)
        return DelegatingSession(lease.handle.session) {
            lease.close()
            registry.unload(RECOMBINE_MODEL_ID)
        }
    }

    private class SessionHandle(val session: LlamaSession) : ModelHandle {
        override fun close() = session.close()
    }

    /** A [LlamaSession] whose `close` runs [onClose] (release + unload) instead of closing directly. */
    private class DelegatingSession(
        private val delegate: LlamaSession,
        private val onClose: () -> Unit
    ) : LlamaSession {
        override fun generate(
            prompt: String,
            maxTokens: Int,
            seed: Int,
            temperature: Float,
            onToken: (String) -> Unit
        ): Int = delegate.generate(prompt, maxTokens, seed, temperature, onToken)

        override fun close() = onClose()
    }

    companion object {
        /** Qwen2.5-0.5B-Instruct-GGUF is Apache-2.0. */
        val QWEN_LICENSE = ModelLicense(LicenseKind.Permissive, spdxId = "Apache-2.0")
    }
}
