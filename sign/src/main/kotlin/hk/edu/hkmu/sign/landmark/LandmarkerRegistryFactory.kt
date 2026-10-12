package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistry

/**
 * Builds the shared [ModelRegistry] for a landmarker manifest entry.
 *
 * The registry is the same lifecycle + **license gate** used by every other model (dep 9 + dep 25):
 * `register` records the entry, and the first `acquire` evaluates the entry's license against
 * `intent` *before* invoking `loader`, so a restricted landmarker can never be brought into memory.
 * Exposed internally so a host test can substitute a fake [ModelLoader] and observe the gate with no
 * MediaPipe native.
 */
internal object LandmarkerRegistryFactory {

    /**
     * Creates and primes a registry for [entry] backed by `loader`.
     *
     * @param licensePolicy Gate consulted before any load.
     * @param intent How the host intends to use the landmarker.
     */
    fun create(
        entry: LandmarkerManifestEntry,
        loader: ModelLoader<LandmarkerModelHandle>,
        licensePolicy: LicensePolicy,
        intent: UsageIntent
    ): ModelRegistry<LandmarkerModelHandle> {
        val registry = ModelRegistry(loader, licensePolicy, intent)
        registry.register(entry.toRegistration())
        return registry
    }
}
