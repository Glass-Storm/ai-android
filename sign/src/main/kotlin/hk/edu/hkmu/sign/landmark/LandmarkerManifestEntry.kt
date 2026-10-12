package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ModelFormat

/**
 * A landmarker artifact as it would appear in a **signed manifest** entry.
 *
 * This mirrors the trusted `models.manifest.ModelDescriptor` shape (id + license + format + ABI) for
 * the `.task` bundle, so the landmarker is loaded through the same trust path as every other model:
 * the license travels with the entry, a `.task` is declared with [ModelFormat.Task], and a
 * MediaPipe bundle is ABI-independent ([Abi.Any]). [toRegistration] adapts it to the lifecycle-only
 * [ModelRegistration] the shared registry consumes, which runs the license gate before load.
 *
 * @property modelId Stable registry id.
 * @property license License evaluated by the active `LicensePolicy` before load.
 * @property fileName Bundle file name inside the model directory.
 * @property format Manifest format token (always [ModelFormat.Task] here).
 * @property abi ABI set (always `{Any}` — the `.task` is not a per-ABI native artifact).
 */
data class LandmarkerManifestEntry(
    val modelId: String,
    val license: ModelLicense,
    val fileName: String,
    val format: ModelFormat = ModelFormat.Task,
    val abi: Set<Abi> = setOf(Abi.Any)
) {
    /** Adapts this entry to the lifecycle-only shape the shared [hk.edu.hkmu.core.registry.ModelRegistry] takes. */
    fun toRegistration(): ModelRegistration = ModelRegistration(modelId, license)
}

/**
 * Known MediaPipe landmarker manifest entries and their licenses.
 *
 * Both the Hand and Holistic `.task` bundles ship under the Apache-2.0 MediaPipe Tasks Vision
 * license, so they pass [hk.edu.hkmu.core.license.UsageIntent.Commercial] as well as `SelfUse`.
 */
object LandmarkerModelCatalog {

    /** Registry id of the hand-only landmarker (`num_hands` configurable). */
    const val HAND_MODEL_ID: String = "mediapipe-hand-landmarker"

    /** Registry id of the holistic landmarker (pose + both hands). */
    const val HOLISTIC_MODEL_ID: String = "mediapipe-holistic-landmarker"

    /** Apache-2.0, per the MediaPipe Tasks Vision distribution. */
    val MEDIAPIPE_LICENSE: ModelLicense = ModelLicense(
        kind = LicenseKind.Permissive,
        spdxId = "Apache-2.0",
        notice = "MediaPipe Tasks Vision and its hand/holistic .task bundles are Apache-2.0."
    )

    /** Hand-only landmarker entry (`hand_landmarker.task`). */
    val HAND_ENTRY: LandmarkerManifestEntry = LandmarkerManifestEntry(
        modelId = HAND_MODEL_ID,
        license = MEDIAPIPE_LICENSE,
        fileName = "hand_landmarker.task"
    )

    /** Holistic landmarker entry (`holistic_landmarker.task`) — the 55-keypoint source. */
    val HOLISTIC_ENTRY: LandmarkerManifestEntry = LandmarkerManifestEntry(
        modelId = HOLISTIC_MODEL_ID,
        license = MEDIAPIPE_LICENSE,
        fileName = "holistic_landmarker.task"
    )
}
