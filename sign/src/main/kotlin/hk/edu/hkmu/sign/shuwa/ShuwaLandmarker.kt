package hk.edu.hkmu.sign.shuwa

import android.content.Context
import android.graphics.Bitmap
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.sign.landmark.LandmarkerManifestEntry
import hk.edu.hkmu.sign.landmark.LandmarkerModelCatalog
import java.io.File

/**
 * Device landmark extractor for the Shuwa pipeline: one decoded frame in, one [ShuwaFrame] out.
 *
 * This is the "eyes" half of the port. It runs MediaPipe's holistic graph so pose **and** the face mesh
 * **and** both hands come from the same frame, which is what the translator's four inputs require.
 *
 * ### Laziness and licensing
 * Construction touches no native code. The first [detect] goes through the shared [ModelRegistry],
 * which evaluates the entry's license against the intent and only then asks the loader to validate the
 * bundle, load `libmediapipe_tasks_vision_jni.so` and build the landmarker. The resulting handle is
 * pinned until [close].
 *
 * ### Capture
 * The library never opens a camera or a decoder: the host supplies an already decoded [Bitmap] and its
 * presentation timestamp. At 15 fps a 16-frame Shuwa window costs about one second of capture.
 */
class ShuwaLandmarker private constructor(
    private val registry: ModelRegistry<ShuwaLandmarkerHandle>?,
    private val modelId: String,
    private val testingBackend: ShuwaLandmarkerBackend?,
    private val testingClose: (() -> Unit)?
) : AutoCloseable {

    private var lease: ModelLease<ShuwaLandmarkerHandle>? = null

    /**
     * Extracts the Shuwa parts of one frame.
     *
     * @param image decoded frame; MediaPipe resizes internally.
     * @param timestampMillis presentation timestamp in milliseconds.
     * @throws hk.edu.hkmu.core.error.AiAndroidException.LicenseRestricted when the license gate refuses
     *   the bundle.
     * @throws hk.edu.hkmu.core.error.AiAndroidException.ModelAssetMissing when the bundle is absent.
     * @throws hk.edu.hkmu.core.error.AiAndroidException.NativeLibraryMissing when the MediaPipe JNI is
     *   unavailable.
     */
    fun detect(image: Bitmap, timestampMillis: Long): ShuwaFrame =
        testingBackend?.detect(image, timestampMillis) ?: handle().backend.detect(image, timestampMillis)

    /** Releases the pinned landmarker. Idempotent. */
    override fun close() {
        lease?.close()
        lease = null
        testingClose?.invoke()
    }

    private fun handle(): ShuwaLandmarkerHandle {
        lease?.let { return it.handle }
        val acquired = registry?.acquire(modelId)
        lease = acquired
        return requireNotNull(acquired).handle
    }

    companion object {
        /**
         * Builds a landmarker for the bundle named by [entry] under [modelDir].
         *
         * @param context Android context.
         * @param modelDir directory holding the provisioned `.task` bundle.
         * @param entry manifest entry (id + license + file name); defaults to the holistic bundle.
         * @param licensePolicy gate consulted before load.
         * @param intent self-use vs commercial.
         * @param nativeLoader shared native loader.
         */
        @Suppress("LongParameterList")
        fun create(
            context: Context,
            modelDir: File,
            entry: LandmarkerManifestEntry = LandmarkerModelCatalog.HOLISTIC_ENTRY,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            nativeLoader: NativeLibraryLoader = NativeLibraryLoader()
        ): ShuwaLandmarker {
            val loader =
                ShuwaLandmarkerModelLoader(
                    context = context,
                    modelId = entry.modelId,
                    taskFile = File(modelDir, entry.fileName),
                    nativeLoader = nativeLoader
                )
            val registry = ModelRegistry(loader, licensePolicy, intent)
            registry.register(entry.toRegistration())
            return ShuwaLandmarker(
                registry = registry,
                modelId = entry.modelId,
                testingBackend = null,
                testingClose = null
            )
        }

        /** Test seam: a landmarker over an injected backend, with no registry and no native code. */
        internal fun forTesting(backend: ShuwaLandmarkerBackend): ShuwaLandmarker = ShuwaLandmarker(
            registry = null,
            modelId = TEST_MODEL_ID,
            testingBackend = backend,
            testingClose = backend::close
        )

        private const val TEST_MODEL_ID = "shuwa-landmarker-test"
    }
}
