package hk.edu.hkmu.sign.gesture

import android.content.Context
import hk.edu.hkmu.core.native.NativeLibraryLoader
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import java.io.File

/**
 * [ModelLoader] that turns a validated `.task` bundle into a native [GestureModelHandle].
 *
 * It is the single place that touches the MediaPipe native surface, and it runs only **after** the
 * shared registry's license gate has permitted the model. Order of operations, fail-fast:
 * 1. [GestureModelAssets.ensurePresent] — typed `ModelAssetMissing` from pure JVM code.
 * 2. [NativeLibraryLoader.load] — typed `NativeLibraryMissing` for `libmediapipe_tasks_vision_jni.so`.
 * 3. MediaPipe `createFromOptions` — real native bundle load.
 *
 * @param context Android context (MediaPipe needs it for asset/CSV resolution).
 * @param modelId Registry id, used in typed errors.
 * @param taskFile Provisioned `.task` bundle path.
 * @param minHandDetectionConfidence Palm-detection confidence threshold.
 * @param minTrackingConfidence Hand-tracking confidence threshold.
 * @param minHandPresenceConfidence Hand-presence confidence threshold.
 * @param nativeLoader Shared JVM native loader.
 */
@Suppress("LongParameterList")
internal class MediaPipeGestureModelLoader(
    private val context: Context,
    private val modelId: String,
    private val taskFile: File,
    private val minHandDetectionConfidence: Float,
    private val minTrackingConfidence: Float,
    private val minHandPresenceConfidence: Float,
    private val nativeLoader: NativeLibraryLoader
) : ModelLoader<GestureModelHandle> {

    override fun load(registration: ModelRegistration): GestureModelHandle {
        GestureModelAssets.ensurePresent(modelId, taskFile)
        nativeLoader.load(NATIVE_LIBRARY)
        val backend = MediaPipeGestureBackend(
            context = context,
            taskFile = taskFile,
            minHandDetectionConfidence = minHandDetectionConfidence,
            minTrackingConfidence = minTrackingConfidence,
            minHandPresenceConfidence = minHandPresenceConfidence
        )
        return GestureModelHandle(backend)
    }

    companion object {
        /** MediaPipe Tasks Vision JNI library packaged by `com.google.mediapipe:tasks-vision`. */
        const val NATIVE_LIBRARY: String = "mediapipe_tasks_vision_jni"
    }
}
