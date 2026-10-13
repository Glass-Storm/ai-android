package hk.edu.hkmu.sign.gesture

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.io.File

/**
 * Device-only [GestureBackend] over MediaPipe Tasks Vision's `GestureRecognizer`.
 *
 * ### Why this is isolated
 * `GestureRecognizer` initialises native in a `static {}` block
 * (`System.loadLibrary("mediapipe_tasks_vision_jni")`), so merely touching the class on a host JVM
 * throws `UnsatisfiedLinkError`. This class is the only place that references it; it is created by
 * [MediaPipeGestureModelLoader] only after the registry license gate, and never in a Tier A test.
 *
 * ### Mode
 * The recognizer runs in `RunningMode.IMAGE` (per-image static recognition), matching the caller
 * contract of [GestureBackend.recognize]. The model asset is resolved by absolute path so a
 * caller-provisioned `.task` bundle is used rather than an APK asset.
 *
 * ### Handedness caveat
 * MediaPipe reports handedness assuming a mirrored (selfie) frame; a third-person view may need the
 * sides flipped. The reported label is surfaced as-is.
 *
 * @param context Android context.
 * @param taskFile Provisioned `.task` bundle.
 * @param minHandDetectionConfidence Palm-detection confidence threshold.
 * @param minTrackingConfidence Hand-tracking confidence threshold.
 * @param minHandPresenceConfidence Hand-presence confidence threshold.
 */
internal class MediaPipeGestureBackend(
    context: Context,
    taskFile: File,
    minHandDetectionConfidence: Float,
    minTrackingConfidence: Float,
    minHandPresenceConfidence: Float
) : GestureBackend {

    private val recognizer: GestureRecognizer = createRecognizer(
        context = context,
        taskFile = taskFile,
        minHandDetectionConfidence = minHandDetectionConfidence,
        minTrackingConfidence = minTrackingConfidence,
        minHandPresenceConfidence = minHandPresenceConfidence
    )

    override fun recognize(image: Bitmap): GestureBackendResult {
        val mpImage = BitmapImageBuilder(image).build()
        return try {
            fromResult(recognizer.recognize(mpImage))
        } finally {
            mpImage.close()
        }
    }

    override fun close() {
        recognizer.close()
    }

    private companion object {

        fun createRecognizer(
            context: Context,
            taskFile: File,
            minHandDetectionConfidence: Float,
            minTrackingConfidence: Float,
            minHandPresenceConfidence: Float
        ): GestureRecognizer {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(taskFile.absolutePath)
                .build()
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            return GestureRecognizer.createFromOptions(context, options)
        }

        /** Projects the MediaPipe result onto the MediaPipe-free [GestureBackendResult]. */
        fun fromResult(result: GestureRecognizerResult): GestureBackendResult {
            val gestures = result.gestures()
            val top = gestures.firstOrNull()?.maxByOrNull { it.score() }
            return GestureBackendResult(
                label = top?.categoryName() ?: "",
                score = top?.score() ?: 0f,
                handedness = result.handedness().firstOrNull()?.firstOrNull()?.categoryName(),
                handCount = gestures.size
            )
        }
    }
}
