package hk.edu.hkmu.sign.shuwa

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import hk.edu.hkmu.sign.landmark.Keypoint3D
import java.io.File

/**
 * Device-only [ShuwaLandmarkerBackend] over MediaPipe Tasks Vision's `HolisticLandmarker`.
 *
 * The reference toolkit runs the legacy `mp.solutions.holistic.Holistic` graph with
 * `model_complexity=1`, `smooth_landmarks=false` and detection/tracking confidence `0.5`; this backend
 * requests the same confidences from the Tasks graph and the same IMAGE running mode, so one detect
 * call corresponds to exactly one frame.
 *
 * ### Why face landmarks are requested
 * Shuwa's face branch consumes 25 of the 468 mesh points (nose, lip contour, both eyes). The hand-only
 * and pose-only paths cannot supply them, so the holistic graph is the only correct source. The Tasks
 * graph reports the canonical mesh, so the reference selection indices remain valid even when the
 * implementation also emits extra iris points.
 *
 * ### Why this class is isolated
 * `HolisticLandmarker` loads `libmediapipe_tasks_vision_jni.so` in a static initialiser, so touching it
 * on a host JVM fails. The model loader creates it only after the registry's license gate passed and
 * the native library was loaded explicitly.
 *
 * @param context Android context MediaPipe needs for asset resolution.
 * @param taskFile provisioned `holistic_landmarker.task` bundle.
 */
internal class MediaPipeShuwaLandmarkerBackend(
    context: Context,
    taskFile: File
) : ShuwaLandmarkerBackend {

    private val holistic: HolisticLandmarker = create(context, taskFile)

    override fun detect(image: Bitmap, timestampMillis: Long): ShuwaFrame {
        val mpImage = BitmapImageBuilder(image).build()
        return try {
            fromHolistic(holistic.detect(mpImage), timestampMillis)
        } finally {
            mpImage.close()
        }
    }

    override fun close() {
        holistic.close()
    }

    private fun fromHolistic(result: HolisticLandmarkerResult, timestampMillis: Long): ShuwaFrame {
        val pose =
            result.poseLandmarks().map { landmark ->
                ShuwaPosePoint(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(MISSING_VISIBILITY)
                )
            }
        val face = result.faceLandmarks().map { it.toKeypoint3D() }
        val left = result.leftHandLandmarks().map { it.toKeypoint3D() }
        val right = result.rightHandLandmarks().map { it.toKeypoint3D() }
        return ShuwaFrame(
            pose = pose,
            face = face,
            leftHand = left,
            rightHand = right,
            timestampMillis = timestampMillis
        )
    }

    private companion object {
        /** MediaPipe omits visibility for non-pose landmarks; pose always supplies it. */
        const val MISSING_VISIBILITY = 0f
        const val DETECTION_CONFIDENCE = 0.5f
        const val TRACKING_CONFIDENCE = 0.5f

        fun create(context: Context, taskFile: File): HolisticLandmarker {
            val options =
                HolisticLandmarker.HolisticLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelAssetPath(taskFile.absolutePath).build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinFaceDetectionConfidence(DETECTION_CONFIDENCE)
                    .setMinFacePresenceConfidence(TRACKING_CONFIDENCE)
                    .setMinPoseDetectionConfidence(DETECTION_CONFIDENCE)
                    .setMinPosePresenceConfidence(TRACKING_CONFIDENCE)
                    .setMinHandLandmarksConfidence(TRACKING_CONFIDENCE)
                    .build()
            return HolisticLandmarker.createFromOptions(context, options)
        }
    }
}

/** Projects a MediaPipe normalized landmark onto `(x, y, z)`. */
private fun NormalizedLandmark.toKeypoint3D(): Keypoint3D = Keypoint3D(x(), y(), z())
