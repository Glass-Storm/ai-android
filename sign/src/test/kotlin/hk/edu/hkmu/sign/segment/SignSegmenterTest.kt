@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.segment

import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-A acceptance for the continuous-input [SignSegmenter].
 *
 * Every case is synthetic and deterministic: offsets are a pure function of the frame index, so the
 * expected boundary positions are computed by hand rather than observed.
 */
class SignSegmenterTest {

    @Test
    fun `synthetic motion stream emits N boundaries at the expected positions`() = runTest {
        val config = SignSegmenterConfig(windowSize = 10, stride = 10, motionThreshold = 0f)
        val frames = SegmenterFixtures.stream(40) { index ->
            if (index in 10 until 30) SegmenterFixtures.MOTION_STEP * (index - 10) else 0f
        }

        val windows = SignSegmenter(config).segment(SegmenterFixtures.streamOf(frames)).toList()

        assertEquals(2, windows.size)
        assertEquals(listOf(0, 1), windows.map { it.index })
        assertEquals(listOf(330L, 660L), windows.map { it.startMillis })
        assertEquals(listOf(627L, 957L), windows.map { it.endMillis })
        assertEquals(frames.subList(10, 20), windows[0].frames)
        assertEquals(frames.subList(20, 30), windows[1].frames)
        assertTrue(windows.all { it.motionScore > 0f })
    }

    @Test
    fun `a no-motion stream emits zero boundaries`() = runTest {
        val config = SignSegmenterConfig(windowSize = 10, stride = 5, motionThreshold = 0f)
        val windows = SignSegmenter(config)
            .segment(SegmenterFixtures.streamOf(SegmenterFixtures.idleStream(100)))
            .toList()

        assertTrue(windows.isEmpty())
    }

    @Test
    fun `an empty stream emits nothing`() = runTest {
        val windows = SignSegmenter(SignSegmenterConfig(windowSize = 10, stride = 10))
            .segment(SegmenterFixtures.streamOf(emptyList()))
            .toList()

        assertTrue(windows.isEmpty())
    }

    @Test
    fun `a stream shorter than one window emits nothing`() = runTest {
        val frames = SegmenterFixtures.stream(5) { SegmenterFixtures.MOTION_STEP * it }
        val windows = SignSegmenter(SignSegmenterConfig(windowSize = 10, stride = 10))
            .segment(SegmenterFixtures.streamOf(frames))
            .toList()

        assertTrue(windows.isEmpty())
    }

    @Test
    fun `a full window emits exactly once when the stream ends`() = runTest {
        val frames = SegmenterFixtures.stream(10) { SegmenterFixtures.MOTION_STEP * it }
        val windows = SignSegmenter(SignSegmenterConfig(windowSize = 10, stride = 10))
            .segment(SegmenterFixtures.streamOf(frames))
            .toList()

        assertEquals(1, windows.size)
        assertEquals(0L, windows.single().startMillis)
        assertEquals(9 * SegmenterFixtures.FRAME_PERIOD_MILLIS, windows.single().endMillis)
    }

    @Test
    fun `a trailing partial window is discarded at stream end`() = runTest {
        val frames = SegmenterFixtures.stream(15) { SegmenterFixtures.MOTION_STEP * it }
        val windows = SignSegmenter(SignSegmenterConfig(windowSize = 10, stride = 10))
            .segment(SegmenterFixtures.streamOf(frames))
            .toList()

        assertEquals(1, windows.size)
        assertEquals(frames.subList(0, 10), windows.single().frames)
    }

    @Test
    fun `overlapping windows share frames`() = runTest {
        val config = SignSegmenterConfig(windowSize = 4, stride = 2)
        assertEquals(2, config.overlap)
        val frames = SegmenterFixtures.stream(8) { SegmenterFixtures.MOTION_STEP * it }

        val windows = SignSegmenter(config).segment(SegmenterFixtures.streamOf(frames)).toList()

        assertEquals(3, windows.size)
        assertEquals(frames.subList(0, 4), windows[0].frames)
        assertEquals(frames.subList(2, 6), windows[1].frames)
        assertEquals(frames.subList(4, 8), windows[2].frames)
    }

    @Test
    fun `the motion gate skips idle boundaries without breaking cadence`() = runTest {
        val config = SignSegmenterConfig(windowSize = 10, stride = 10, motionThreshold = 0f)
        val frames = SegmenterFixtures.stream(30) { index ->
            if (index in 10 until 20) 0f else SegmenterFixtures.MOTION_STEP * index
        }

        val windows = SignSegmenter(config).segment(SegmenterFixtures.streamOf(frames)).toList()

        assertEquals(listOf(0, 1), windows.map { it.index })
        assertEquals(listOf(0L, 660L), windows.map { it.startMillis })
    }

    @Test
    fun `a frame with a different keypoint count is rejected`() {
        val session = SegmentationSession(SignSegmenterConfig(windowSize = 4, stride = 4))
        val frames = listOf(
            SegmenterFixtures.idle(0),
            SegmenterFixtures.idle(1),
            SegmenterFixtures.frame(2, offset = 0f, keypoints = 3),
            SegmenterFixtures.idle(3)
        )

        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            frames.forEach { session.accept(it) }
        }

        assertTrue(exception.reason.contains("keypoint count changed"))
    }

    @Test
    fun `config rejects invalid window stride and threshold`() {
        assertThrows(IllegalArgumentException::class.java) { SignSegmenterConfig(windowSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { SignSegmenterConfig(windowSize = 4, stride = 0) }
        assertThrows(IllegalArgumentException::class.java) { SignSegmenterConfig(windowSize = 4, stride = 5) }
        assertThrows(IllegalArgumentException::class.java) { SignSegmenterConfig(motionThreshold = -1f) }
        assertThrows(IllegalArgumentException::class.java) { SignSegmenterConfig(motionThreshold = Float.NaN) }
    }
}
