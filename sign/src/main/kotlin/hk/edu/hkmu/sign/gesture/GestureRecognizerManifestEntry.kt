package hk.edu.hkmu.sign.gesture

import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.sign.landmark.LandmarkerModelCatalog

/**
 * A static-gesture recognizer artifact as it would appear in a **signed manifest** entry.
 *
 * This mirrors the trusted `models.manifest.ModelDescriptor` shape (id + license + file name) for
 * the MediaPipe `.task` bundle, so the gesture recognizer is loaded through the same trust path as
 * every other model: the license travels with the entry and [toRegistration] adapts it to the
 * lifecycle-only [ModelRegistration] the shared registry consumes, which runs the license gate
 * before load.
 *
 * @property modelId Stable registry id.
 * @property license License evaluated by the active `LicensePolicy` before load.
 * @property fileName Bundle file name inside the model directory.
 */
class GestureRecognizerManifestEntry(
    val modelId: String,
    val license: ModelLicense,
    val fileName: String
) {
    /** Adapts this entry to the lifecycle-only shape the shared registry takes. */
    fun toRegistration(): ModelRegistration = ModelRegistration(modelId, license)
}

/**
 * The known MediaPipe static-gesture manifest entry and its license.
 *
 * The `gesture_recognizer.task` bundle ships under the same Apache-2.0 MediaPipe Tasks Vision
 * license as the landmarker bundles, so [LandmarkerModelCatalog.MEDIAPIPE_LICENSE] is reused
 * verbatim: it passes `Commercial` as well as `SelfUse`.
 */
object GestureRecognizerModelCatalog {

    /** Registry id of the MediaPipe static-gesture recognizer. */
    const val GESTURE_MODEL_ID: String = "mediapipe-gesture-recognizer"

    /** Apache-2.0 MediaPipe Tasks Vision license (shared with the landmarker catalog). */
    val MEDIAPIPE_LICENSE: ModelLicense = LandmarkerModelCatalog.MEDIAPIPE_LICENSE

    /** Static-gesture recognizer entry (`gesture_recognizer.task`). */
    val GESTURE_ENTRY: GestureRecognizerManifestEntry = GestureRecognizerManifestEntry(
        modelId = GESTURE_MODEL_ID,
        license = MEDIAPIPE_LICENSE,
        fileName = "gesture_recognizer.task"
    )
}
