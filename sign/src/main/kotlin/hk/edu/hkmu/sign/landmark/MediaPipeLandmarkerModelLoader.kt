package hk.edu.hkmu.sign.landmark

import android.content.Context
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import java.io.File

/**
 * [ModelLoader] that turns a validated `.task` bundle into a native [LandmarkerModelHandle].
 *
 * It is the single place that touches the MediaPipe native surface, and it runs only **after** the
 * shared registry's license gate has permitted the model. Order of operations, fail-fast:
 * 1. [LandmarkerModelAssets.ensurePresent] — typed `ModelAssetMissing` from pure JVM (dep 25 path).
 * 2. [NativeLibraryLoader.load] — typed `NativeLibraryMissing` for `libmediapipe_tasks_vision_jni.so`
 *    (reuses the Wave-0 native loader, dep 8).
 * 3. MediaPipe `createFromOptions` — real native bundle load; `num_hands` is applied here.
 *
 * @param context Android context (MediaPipe needs it for asset/CSV resolution).
 * @param modelId Registry id, used in typed errors.
 * @param taskFile Provisioned `.task` bundle path.
 * @param mode Which landmarker graph to create.
 * @param numHands Hands to track for [LandmarkerMode.Hand] (2 for sign).
 * @param topology Keypoint order the assembler will emit.
 * @param nativeLoader Shared JVM native loader (dep 8).
 */
@Suppress("LongParameterList")
internal class MediaPipeLandmarkerModelLoader(
    private val context: Context,
    private val modelId: String,
    private val taskFile: File,
    private val mode: LandmarkerMode,
    private val numHands: Int,
    private val topology: LandmarkTopology,
    private val nativeLoader: NativeLibraryLoader
) : ModelLoader<LandmarkerModelHandle> {

    override fun load(registration: ModelRegistration): LandmarkerModelHandle {
        LandmarkerModelAssets.ensurePresent(modelId, taskFile)
        nativeLoader.load(NATIVE_LIBRARY)
        val backend = MediaPipeLandmarkerBackend(context, taskFile, mode, numHands)
        return LandmarkerModelHandle(backend, LandmarkResultAssembler(topology))
    }

    companion object {
        /** MediaPipe Tasks Vision JNI library packaged by `com.google.mediapipe:tasks-vision`. */
        const val NATIVE_LIBRARY: String = "mediapipe_tasks_vision_jni"
    }
}
