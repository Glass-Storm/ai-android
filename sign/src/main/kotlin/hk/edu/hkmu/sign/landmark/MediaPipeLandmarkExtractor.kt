package hk.edu.hkmu.sign.landmark

import android.content.Context
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.core.registry.ModelRegistry
import java.io.File

/**
 * Production [LandmarkExtractor]: MediaPipe Tasks Vision landmarker on caller-supplied frames.
 *
 * The native landmarker is **not** created at construction. The first [extract] call goes through the
 * shared [ModelRegistry], which runs the license gate and then
 * [MediaPipeLandmarkerModelLoader.load]; the resulting handle is pinned for the extractor's lifetime
 * and freed by [close]. This mirrors the lazy/native-free-at-construction contract used by the other
 * engines, so an extractor can be built on a host without loading MediaPipe.
 *
 * The extractor never opens a camera: frames are supplied by the caller as [VideoFrame]s.
 */
class MediaPipeLandmarkExtractor internal constructor(
    private val registry: ModelRegistry<LandmarkerModelHandle>,
    private val modelId: String
) : LandmarkExtractor {

    private var lease: ModelLease<LandmarkerModelHandle>? = null

    override fun extract(frame: VideoFrame): LandmarkFrame = handle().detect(frame)

    /** Releases the pinned landmarker handle. Idempotent. */
    override fun close() {
        lease?.close()
        lease = null
    }

    private fun handle(): LandmarkerModelHandle {
        lease?.let { return it.handle }
        val acquired = registry.acquire(modelId)
        lease = acquired
        return acquired.handle
    }

    companion object {
        /** Sign reading tracks both hands. */
        const val DEFAULT_NUM_HANDS: Int = 2

        /**
         * Builds an extractor for the `.task` bundle named by [entry] under [modelDir].
         *
         * The bundle is loaded lazily (first [extract]); a missing bundle yields
         * [hk.edu.hkmu.core.error.AiAndroidException.ModelAssetMissing], a missing native library
         * `NativeLibraryMissing`, and a restricted license `LicenseRestricted` — all before the
         * native landmarker is built.
         *
         * @param context Android context.
         * @param modelDir Directory holding the provisioned `.task` bundle (host-provisioned).
         * @param entry Manifest entry (id + license + file name); defaults to the holistic bundle.
         * @param mode Landmarker graph; [LandmarkerMode.Holistic] yields the 55-keypoint contract.
         * @param numHands Hands to track for [LandmarkerMode.Hand].
         * @param topology Per-frame keypoint order the assembler emits.
         * @param licensePolicy Gate consulted before load.
         * @param intent Self-use vs commercial.
         * @param nativeLoader Shared JVM native loader.
         */
        @Suppress("LongParameterList")
        fun create(
            context: Context,
            modelDir: File,
            entry: LandmarkerManifestEntry = LandmarkerModelCatalog.HOLISTIC_ENTRY,
            mode: LandmarkerMode = LandmarkerMode.Holistic,
            numHands: Int = DEFAULT_NUM_HANDS,
            topology: LandmarkTopology = LandmarkTopology.UPPER_BODY_HANDS,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            nativeLoader: NativeLibraryLoader = NativeLibraryLoader()
        ): MediaPipeLandmarkExtractor {
            val loader = MediaPipeLandmarkerModelLoader(
                context = context,
                modelId = entry.modelId,
                taskFile = File(modelDir, entry.fileName),
                mode = mode,
                numHands = numHands,
                topology = topology,
                nativeLoader = nativeLoader
            )
            val registry = LandmarkerRegistryFactory.create(entry, loader, licensePolicy, intent)
            return MediaPipeLandmarkExtractor(registry, entry.modelId)
        }
    }
}
