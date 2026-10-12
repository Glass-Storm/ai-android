package hk.edu.hkmu.core.registry

import hk.edu.hkmu.core.license.ModelLicense

/**
 * The minimal model descriptor [ModelRegistry] operates on.
 *
 * Carries only what the lifecycle needs: a stable [modelId] and the [license] that gates
 * instantiation. The signed-manifest `ModelDescriptor` produced by the `models` module conforms to
 * this shape by exposing the same two properties, so the registry never depends on the manifest
 * schema or on any engine.
 *
 * @property modelId Stable identifier used as the registry key.
 * @property license Per-model license evaluated by the injected
 *   [hk.edu.hkmu.core.license.LicensePolicy] before the model is instantiated.
 */
data class ModelRegistration(
    val modelId: String,
    val license: ModelLicense
)
