@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.landmark

import android.graphics.Bitmap
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration

/** Deterministic synthetic landmark window matching a [ClassifierInputSpec]. */
internal object LandmarkTestFixtures {

    /** Per-keypoint `x` for keypoint [keypoint] at frame [frame]; kept inside `[0, 1]`. */
    fun x(keypoint: Int, frame: Int): Float = (keypoint + frame).toFloat() / X_DENOMINATOR

    /** Per-keypoint `y` for keypoint [keypoint] at frame [frame]; kept inside `[0, 1]`. */
    fun y(keypoint: Int, frame: Int): Float = (keypoint * FRAMES + frame).toFloat() / Y_DENOMINATOR

    /** Builds a `windowSize x keypointsPerFrame` landmark stream with [x]/[y] values. */
    fun window(windowSize: Int, keypointsPerFrame: Int): List<LandmarkFrame> {
        return (0 until windowSize).map { frame ->
            LandmarkFrame(
                keypoints = (0 until keypointsPerFrame).map { keypoint ->
                    Keypoint2D(x(keypoint, frame), y(keypoint, frame))
                },
                timestampMillis = frame.toLong() * FRAME_PERIOD_MILLIS
            )
        }
    }

    private const val X_DENOMINATOR = 105f
    private const val Y_DENOMINATOR = 5500f
    private const val FRAMES = 50
    private const val FRAME_PERIOD_MILLIS = 33L
}

/** A [LandmarkerBackend] that never touches MediaPipe native. */
internal class FakeLandmarkerBackend(
    private val result: RawLandmarkFrame = RawLandmarkFrame(emptyList(), emptyList(), emptyList(), 0L)
) : LandmarkerBackend {
    var detectCount: Int = 0
        private set
    var closed: Boolean = false
        private set

    override fun detect(image: Bitmap, timestampMillis: Long): RawLandmarkFrame {
        detectCount++
        return result
    }

    override fun close() {
        closed = true
    }
}

/** A [ModelLoader] recording whether it was ever invoked (used to prove the license gate). */
internal class RecordingLandmarkerLoader(
    private val backend: LandmarkerBackend = FakeLandmarkerBackend(),
    private val topology: LandmarkTopology = LandmarkTopology.UPPER_BODY_HANDS
) : ModelLoader<LandmarkerModelHandle> {
    var loadCount: Int = 0
        private set

    override fun load(registration: ModelRegistration): LandmarkerModelHandle {
        loadCount++
        return LandmarkerModelHandle(backend, LandmarkResultAssembler(topology))
    }
}
