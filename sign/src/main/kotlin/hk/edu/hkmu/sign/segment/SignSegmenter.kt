package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Segments a **continuous, unbounded** landmark stream into classifier windows.
 *
 * Feed it the [LandmarkFrame] stream produced by the landmark extractor (todo 19); it emits one
 * [SignWindow] each time a full window has been accumulated — the "emit at the window boundary"
 * contract. It never requires a fixed-length video file: it consumes frames as they arrive and emits
 * as windows fill, so a live camera stream works unchanged.
 *
 * ### Emission semantics
 * A boundary slot occurs when the window first fills (`windowSize` frames consumed) and then every
 * [SignSegmenterConfig.stride] frames after that, so consecutive windows overlap by
 * [SignSegmenterConfig.overlap] frames. At each slot the transition gate ([MotionGate]) is evaluated
 * over the current window: when it is open (mean displacement `> motionThreshold`) a [SignWindow] is
 * emitted, otherwise the slot is skipped. Skipping does **not** shift the cadence, so boundary
 * positions are deterministic regardless of motion.
 *
 * ### Stream start and end
 * Nothing is emitted before `windowSize` frames have arrived. When the stream completes, any trailing
 * frames that did not complete a window are discarded and the flow completes normally — there is no
 * forced final flush, matching the core [hk.edu.hkmu.core.engine.SignEngine] "emit at the window
 * boundary" contract.
 *
 * ### Cancellation
 * Cancelling collection discards the in-progress window and stops further emissions.
 *
 * @param config Window/stride/gate tunables; defaults match the core sign defaults.
 */
class SignSegmenter(private val config: SignSegmenterConfig = SignSegmenterConfig()) {

    /**
     * Consumes [frames] and emits a [SignWindow] at each motion-gated window boundary.
     *
     * @throws AiAndroidException.MalformedFrame when a frame's keypoint count differs from the rest of
     *   the in-progress window.
     */
    fun segment(frames: Flow<LandmarkFrame>): Flow<SignWindow> = flow {
        val session = SegmentationSession(config)
        frames.collect { frame ->
            session.accept(frame)?.let { emit(it) }
        }
    }
}

/**
 * Incremental, single-stream segmentation state: one instance per stream collection.
 *
 * Kept pure (no coroutines, no native) so the boundary/gate arithmetic is exercised synchronously in
 * host-JVM tests. Not thread-safe; [SignSegmenter] creates one per flow collection.
 */
internal class SegmentationSession(private val config: SignSegmenterConfig) {

    private val buffer = ArrayDeque<LandmarkFrame>(config.windowSize)
    private var expectedKeypoints: Int? = null
    private var consumed = 0L
    private var nextIndex = 0

    /** Consumes one [frame]; returns a [SignWindow] iff this frame closed a gated boundary. */
    fun accept(frame: LandmarkFrame): SignWindow? {
        validate(frame)
        buffer.addLast(frame)
        if (buffer.size > config.windowSize) {
            buffer.removeFirst()
        }
        consumed++
        if (!atBoundary()) {
            return null
        }
        val score = MotionGate.score(buffer)
        return if (MotionGate.isOpen(score, config.motionThreshold)) emitWindow(score) else null
    }

    private fun validate(frame: LandmarkFrame) {
        val expected = expectedKeypoints
        if (expected == null) {
            expectedKeypoints = frame.keypoints.size
        } else if (frame.keypoints.size != expected) {
            throw AiAndroidException.MalformedFrame(
                "keypoint count changed within a window: expected $expected, got ${frame.keypoints.size}"
            )
        }
    }

    private fun atBoundary(): Boolean =
        consumed >= config.windowSize && (consumed - config.windowSize) % config.stride == 0L

    private fun emitWindow(score: Float): SignWindow {
        val frames = buffer.toList()
        return SignWindow(
            index = nextIndex++,
            frames = frames,
            startMillis = frames.first().timestampMillis,
            endMillis = frames.last().timestampMillis,
            motionScore = score
        )
    }
}
