package hk.edu.hkmu.sign.landmark

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import java.io.File

/**
 * Device-only [LandmarkerBackend] over MediaPipe Tasks Vision.
 *
 * ### Why this is isolated
 * `HandLandmarker`/`HolisticLandmarker` initialise native in a `static {}` block
 * (`System.loadLibrary("mediapipe_tasks_vision_jni")`), so merely touching the class on a host JVM
 * throws `UnsatisfiedLinkError`. This class is the only place that references them; it is created by
 * [MediaPipeLandmarkerModelLoader] only after the registry license gate, and never in a Tier A test.
 *
 * ### `num_hands`
 * The [LandmarkerMode.Hand] graph is created with `setNumHands(numHands)` (2 for sign reading). The
 * [LandmarkerMode.Holistic] graph always tracks both hands — there is no `num_hands` knob — and its
 * result carries `leftHandLandmarks`/`rightHandLandmarks`.
 *
 * ### Handedness caveat
 * MediaPipe reports handedness assuming a mirrored (selfie) frame; the S2 pipeline is a third-person
 * view. The side split here is by the reported label, which is a documented open question for on-device
 * verification — it does not affect the normalization contract.
 *
 * @param context Android context.
 * @param taskFile Provisioned `.task` bundle.
 * @param mode Which graph to run.
 * @param numHands Hands to track for [LandmarkerMode.Hand].
 */
internal class MediaPipeLandmarkerBackend(
    context: Context,
    taskFile: File,
    private val mode: LandmarkerMode,
    private val numHands: Int
) : LandmarkerBackend {

    private val holistic: HolisticLandmarker? =
        if (mode == LandmarkerMode.Holistic) createHolistic(context, taskFile) else null

    private val hand: HandLandmarker? =
        if (mode == LandmarkerMode.Hand) createHand(context, taskFile, numHands) else null

    override fun detect(image: Bitmap, timestampMillis: Long): RawLandmarkFrame {
        val mpImage = BitmapImageBuilder(image).build()
        return try {
            when (mode) {
                LandmarkerMode.Holistic -> fromHolistic(requireNotNull(holistic).detect(mpImage), timestampMillis)
                LandmarkerMode.Hand -> fromHand(requireNotNull(hand).detect(mpImage), timestampMillis)
            }
        } finally {
            mpImage.close()
        }
    }

    override fun close() {
        holistic?.close()
        hand?.close()
    }

    private fun fromHolistic(result: HolisticLandmarkerResult, timestampMillis: Long): RawLandmarkFrame =
        RawLandmarkFrame(
            pose = result.poseLandmarks().map { it.toKeypoint3D() },
            leftHand = result.leftHandLandmarks().map { it.toKeypoint3D() },
            rightHand = result.rightHandLandmarks().map { it.toKeypoint3D() },
            timestampMillis = timestampMillis
        )

    private fun fromHand(result: HandLandmarkerResult, timestampMillis: Long): RawLandmarkFrame {
        var left = emptyList<Keypoint3D>()
        var right = emptyList<Keypoint3D>()
        result.landmarks().forEachIndexed { index, landmarks ->
            val label = result.handedness().getOrNull(index)?.firstOrNull()?.categoryName()
            val points = landmarks.map { it.toKeypoint3D() }
            if (label.equals(HAND_LEFT, ignoreCase = true)) left = points else right = points
        }
        return RawLandmarkFrame(
            pose = emptyList(),
            leftHand = left,
            rightHand = right,
            timestampMillis = timestampMillis
        )
    }

    private companion object {
        const val HAND_LEFT = "Left"

        fun baseOptions(taskFile: File): BaseOptions =
            BaseOptions.builder().setModelAssetPath(taskFile.absolutePath).build()

        fun createHolistic(context: Context, taskFile: File): HolisticLandmarker {
            val options = HolisticLandmarker.HolisticLandmarkerOptions.builder()
                .setBaseOptions(baseOptions(taskFile))
                .setRunningMode(RunningMode.IMAGE)
                .build()
            return HolisticLandmarker.createFromOptions(context, options)
        }

        fun createHand(context: Context, taskFile: File, numHands: Int): HandLandmarker {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions(taskFile))
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(numHands)
                .build()
            return HandLandmarker.createFromOptions(context, options)
        }
    }
}

/** Projects a MediaPipe normalized landmark onto the image plane. */
private fun NormalizedLandmark.toKeypoint3D(): Keypoint3D = Keypoint3D(x(), y(), z())
