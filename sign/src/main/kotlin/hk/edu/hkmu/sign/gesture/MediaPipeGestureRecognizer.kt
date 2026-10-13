@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.gesture

import android.content.Context
import android.graphics.Bitmap
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistry
import java.io.File

/**
 * Production static-gesture recognizer: MediaPipe Tasks Vision's `GestureRecognizer` on
 * caller-supplied images.
 *
 * The native recognizer is **not** created at construction. The first [recognize] call goes through
 * the shared [ModelRegistry], which runs the license gate and then [MediaPipeGestureModelLoader.load];
 * the resulting handle is pinned for the recognizer's lifetime and freed by [close]. This mirrors
 * the lazy/native-free-at-construction contract of [hk.edu.hkmu.sign.landmark.MediaPipeLandmarkExtractor],
 * so a recognizer can be built on a host without loading MediaPipe.
 *
 * The recognizer never opens a camera: images are supplied by the caller as [Bitmap]s.
 *
 * @param registry License-gated registry that owns the native gesture model.
 * @param modelId Registry id acquired lazily on the first [recognize].
 */
class MediaPipeGestureRecognizer internal constructor(
    private val registry: ModelRegistry<GestureModelHandle>,
    private val modelId: String
) : AutoCloseable {

    /**
     * Host-test seam: wraps a caller-supplied [backend] (e.g. a fake) in a registry-backed recognizer
     * so the result mapping can be exercised without touching MediaPipe native.
     */
    internal constructor(backend: GestureBackend) : this(
        GestureRegistryFactory.create(
            entry = GestureRecognizerModelCatalog.GESTURE_ENTRY,
            loader = ModelLoader { _ -> GestureModelHandle(backend) },
            licensePolicy = DefaultLicensePolicy(),
            intent = UsageIntent.SelfUse
        ),
        GestureRecognizerModelCatalog.GESTURE_MODEL_ID
    )

    private var lease: ModelLease<GestureModelHandle>? = null

    /**
     * Recognizes the top static gesture in [image].
     *
     * @return the most confident gesture, or `null` when no hand or no gesture was detected.
     */
    fun recognize(image: Bitmap): GesturePrediction? = handle().recognize(image).toPrediction()

    /** Releases the pinned gesture handle. Idempotent. */
    override fun close() {
        lease?.close()
        lease = null
    }

    private fun handle(): GestureModelHandle {
        lease?.let { return it.handle }
        val acquired = registry.acquire(modelId)
        lease = acquired
        return acquired.handle
    }

    companion object {

        /**
         * Builds a recognizer for the `.task` bundle named by [entry] under [modelDir].
         *
         * The bundle is loaded lazily (first [recognize]); a missing bundle yields
         * [hk.edu.hkmu.core.error.AiAndroidException.ModelAssetMissing], a missing native library
         * `NativeLibraryMissing`, and a restricted license `LicenseRestricted` — all before the
         * native recognizer is built.
         *
         * @param context Android context.
         * @param modelDir Directory holding the provisioned `.task` bundle (host-provisioned).
         * @param entry Manifest entry (id + license + file name); defaults to the gesture bundle.
         * @param minHandDetectionConfidence Palm-detection confidence threshold.
         * @param minTrackingConfidence Hand-tracking confidence threshold.
         * @param minHandPresenceConfidence Hand-presence confidence threshold.
         * @param licensePolicy Gate consulted before load.
         * @param intent Self-use vs commercial.
         * @param nativeLoader Shared JVM native loader.
         */
        @Suppress("LongParameterList")
        fun create(
            context: Context,
            modelDir: File,
            entry: GestureRecognizerManifestEntry = GestureRecognizerModelCatalog.GESTURE_ENTRY,
            minHandDetectionConfidence: Float = 0.5f,
            minTrackingConfidence: Float = 0.5f,
            minHandPresenceConfidence: Float = 0.5f,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            nativeLoader: NativeLibraryLoader = NativeLibraryLoader()
        ): MediaPipeGestureRecognizer {
            val loader = MediaPipeGestureModelLoader(
                context = context,
                modelId = entry.modelId,
                taskFile = File(modelDir, entry.fileName),
                minHandDetectionConfidence = minHandDetectionConfidence,
                minTrackingConfidence = minTrackingConfidence,
                minHandPresenceConfidence = minHandPresenceConfidence,
                nativeLoader = nativeLoader
            )
            val registry = GestureRegistryFactory.create(entry, loader, licensePolicy, intent)
            return MediaPipeGestureRecognizer(registry, entry.modelId)
        }
    }
}

/** Projects the raw backend result into the public prediction, or `null` when no gesture exists. */
private fun GestureBackendResult.toPrediction(): GesturePrediction? =
    if (label.isEmpty()) null else GesturePrediction(label, score, handedness, handCount)
