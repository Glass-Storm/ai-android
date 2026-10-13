package hk.edu.hkmu.sign.gesture

import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Required-file validation for the MediaPipe static-gesture `.task` bundle, run **before** any
 * native call.
 *
 * MediaPipe's `BaseOptions.setModelAssetPath` fails deep in native when the bundle is absent, so
 * resolving it up front turns a quiet "file not there" into a typed
 * [AiAndroidException.ModelAssetMissing] from pure JVM code. Fully unit-testable on the host.
 */
internal object GestureModelAssets {

    /** The MediaPipe Tasks bundle extension. */
    const val TASK_EXTENSION: String = "task"

    /**
     * Validates [taskFile] as a MediaPipe `.task` bundle.
     *
     * @return [taskFile] when valid.
     * @throws AiAndroidException.ModelAssetMissing when the file is absent or not a `.task` bundle.
     */
    fun ensurePresent(modelId: String, taskFile: File): File {
        if (!taskFile.isFile || !taskFile.name.lowercase().endsWith(".$TASK_EXTENSION")) {
            throw AiAndroidException.ModelAssetMissing(modelId, taskFile.path)
        }
        return taskFile
    }
}
