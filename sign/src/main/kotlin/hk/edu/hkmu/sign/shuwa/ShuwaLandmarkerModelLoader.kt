package hk.edu.hkmu.sign.shuwa

import android.content.Context
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.sign.landmark.LandmarkerModelAssets
import hk.edu.hkmu.sign.landmark.MediaPipeLandmarkerModelLoader
import java.io.File

/**
 * [ModelLoader] that turns a validated `holistic_landmarker.task` bundle into a
 * [ShuwaLandmarkerHandle].
 *
 * Order is fail-fast and identical to the hand/holistic extractor's loader:
 * 1. [LandmarkerModelAssets.ensurePresent] - typed `ModelAssetMissing` from pure JVM.
 * 2. [NativeLibraryLoader.load] - typed `NativeLibraryMissing` for the MediaPipe JNI.
 * 3. `HolisticLandmarker.createFromOptions` - the real native bundle load.
 *
 * The registry runs its license gate before calling this loader, so a restricted bundle is never
 * instantiated.
 *
 * @param context Android context for MediaPipe.
 * @param modelId registry id used in typed errors.
 * @param taskFile provisioned holistic bundle.
 * @param nativeLoader shared native loader.
 */
internal class ShuwaLandmarkerModelLoader(
    private val context: Context,
    private val modelId: String,
    private val taskFile: File,
    private val nativeLoader: NativeLibraryLoader
) : ModelLoader<ShuwaLandmarkerHandle> {

    override fun load(registration: ModelRegistration): ShuwaLandmarkerHandle {
        LandmarkerModelAssets.ensurePresent(modelId, taskFile)
        nativeLoader.load(MediaPipeLandmarkerModelLoader.NATIVE_LIBRARY)
        return ShuwaLandmarkerHandle(MediaPipeShuwaLandmarkerBackend(context, taskFile))
    }
}
