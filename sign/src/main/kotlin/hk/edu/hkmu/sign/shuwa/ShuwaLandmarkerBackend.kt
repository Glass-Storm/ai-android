package hk.edu.hkmu.sign.shuwa

import android.graphics.Bitmap
import hk.edu.hkmu.core.registry.ModelHandle

/**
 * Narrow seam over the native holistic landmarker.
 *
 * It returns the MediaPipe-free [ShuwaFrame], so the production backend can be swapped for a fake in a
 * host-JVM test and the lifecycle exercised without `libmediapipe_tasks_vision_jni.so`.
 */
internal interface ShuwaLandmarkerBackend : AutoCloseable {
    /** Runs the landmarker on one decoded frame. */
    fun detect(image: Bitmap, timestampMillis: Long): ShuwaFrame

    /** Releases the native landmarker. Idempotent. */
    override fun close() {}
}

/**
 * Registry handle owning one native landmarker for the whole residency of the model.
 *
 * @property backend the native-backed extractor this handle owns.
 */
internal class ShuwaLandmarkerHandle(
    val backend: ShuwaLandmarkerBackend
) : ModelHandle {
    override fun close() {
        backend.close()
    }
}
