@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.sign.landmark.Keypoint2D
import hk.edu.hkmu.sign.landmark.LandmarkExtractor
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import hk.edu.hkmu.sign.landmark.VideoFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic, native-free fixtures for the sign→text pipeline Tier-A tests.
 *
 * The MediaPipe extraction boundary is device-only (todo 19), so the pipeline is exercised from the
 * assembled-landmark seam onward — exactly as the landmark module itself is tested.
 */
internal object PipelineTestFixtures {

    /** Milliseconds between synthetic frames. */
    const val FRAME_PERIOD_MILLIS = 33L

    /** Coordinate modulus keeping synthetic landmark coordinates inside `[0, 1]`. */
    const val POSITION_MODULUS = 100

    /**
     * A moving stream: [count] frames of [keypoints] landmarks whose coordinates change with the
     * frame index, so the transition gate (strict `> 0`) opens at every boundary and every window is
     * emitted. Emitted windows are therefore in strict frame order.
     */
    fun movingStream(count: Int, keypoints: Int): List<LandmarkFrame> = List(count) { frame ->
        LandmarkFrame(
            keypoints = List(keypoints) { keypoint ->
                Keypoint2D(
                    x = ((keypoint + frame) % POSITION_MODULUS).toFloat() / POSITION_MODULUS,
                    y = frame.toFloat() / POSITION_MODULUS
                )
            },
            timestampMillis = frame.toLong() * FRAME_PERIOD_MILLIS
        )
    }

    /** A motionless stream: every frame (and every keypoint) is identical, so the gate never opens. */
    fun stillStream(count: Int, keypoints: Int): List<LandmarkFrame> = List(count) { frame ->
        LandmarkFrame(
            keypoints = List(keypoints) { Keypoint2D(0.5f, 0.5f) },
            timestampMillis = frame.toLong() * FRAME_PERIOD_MILLIS
        )
    }

    /** Adapts [frames] to the pipeline's landmark flow. */
    fun streamOf(frames: List<LandmarkFrame>): Flow<LandmarkFrame> = frames.asFlow()
}

/**
 * A [LandmarkExtractor] that never touches MediaPipe native.
 *
 * The default [frameFor] throws, which makes it a *proof* that the Tier-A path bypasses extraction
 * (`recognizeLandmarks`); a test that wants the [VideoFrame] path supplies its own mapping.
 */
internal class FakeLandmarkExtractor(
    private val frameFor: (VideoFrame) -> LandmarkFrame = { error("extraction not expected on Tier A") }
) : LandmarkExtractor {

    /** True once [close] was called. */
    var closed: Boolean = false
        private set

    override fun extract(frame: VideoFrame): LandmarkFrame = frameFor(frame)

    override fun close() {
        closed = true
    }
}

/**
 * A [PostProcessor] recording every result the pipeline sends it and emitting a deterministic
 * transform of it, so recombination can be asserted without the native llama.cpp runtime.
 */
internal class RecordingPostProcessor(
    private val transform: (String) -> String = { value -> "[$value]" }
) : PostProcessor {

    /** Inputs passed to [process], in order. Empty proves recombination never ran. */
    val inputs: MutableList<String> = mutableListOf()

    override fun process(text: String): Flow<String> = flow {
        inputs += text
        emit(transform(text))
    }
}
