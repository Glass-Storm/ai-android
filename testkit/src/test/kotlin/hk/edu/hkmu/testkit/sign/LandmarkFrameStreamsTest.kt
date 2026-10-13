@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.sign

import hk.edu.hkmu.sign.segment.SignSegmenter
import hk.edu.hkmu.sign.segment.SignSegmenterConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-A acceptance for the synthetic-landmark fixtures: shape/range, determinism, and that the
 * moving/still sequences drive the real [SignSegmenter] motion gate in opposite directions.
 */
class LandmarkFrameStreamsTest {

    @Test
    fun `landmarks produces the requested keypoint count within the unit square`() {
        val frame = landmarks(count = 55, timestampMillis = 123L, seed = 7)

        assertEquals(55, frame.keypoints.size)
        assertEquals(123L, frame.timestampMillis)
        assertTrue(frame.keypoints.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `landmarks is deterministic and varies with the timestamp and seed`() {
        assertEquals(landmarks(seed = 3, timestampMillis = 40), landmarks(seed = 3, timestampMillis = 40))
        assertNotEquals(
            landmarks(seed = 3, timestampMillis = 40).keypoints,
            landmarks(seed = 3, timestampMillis = 41).keypoints
        )
        assertNotEquals(
            landmarks(seed = 3, timestampMillis = 40).keypoints,
            landmarks(seed = 4, timestampMillis = 40).keypoints
        )
    }

    @Test
    fun `moving frames clear the motion gate while still frames do not`() = runTest {
        val moving = movingFrames(frames = 80)
        val still = stillFrames(frames = 80)

        assertNotEquals("moving keypoints must change", moving.first().keypoints, moving.last().keypoints)
        assertEquals("still keypoints must be identical", still.first().keypoints, still.last().keypoints)

        val config = SignSegmenterConfig(windowSize = 30, stride = 30)
        val movingWindows = SignSegmenter(config).segment(landmarkStream(moving)).toList()
        val stillWindows = SignSegmenter(config).segment(landmarkStream(still)).toList()

        assertTrue("a moving stream must emit windows", movingWindows.isNotEmpty())
        assertTrue("a motionless stream must emit nothing", stillWindows.isEmpty())
    }

    @Test
    fun `moving frames also clear a fifty frame window`() = runTest {
        val config = SignSegmenterConfig(windowSize = 50, stride = 50)

        val windows = SignSegmenter(config).segment(landmarkStream(movingFrames(frames = 100))).toList()

        assertTrue(windows.isNotEmpty())
    }

    @Test
    fun `fewer frames than the window size yields no windows`() = runTest {
        val config = SignSegmenterConfig(windowSize = 30, stride = 10)

        val movingWindows = SignSegmenter(config).segment(landmarkStream(movingFrames(frames = 29))).toList()
        val stillWindows = SignSegmenter(config).segment(landmarkStream(stillFrames(frames = 29))).toList()

        assertTrue("a partial moving window must not be emitted", movingWindows.isEmpty())
        assertTrue("a partial still window must not be emitted", stillWindows.isEmpty())
    }
}
