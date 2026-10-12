@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.segment

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the human-readable segmentation observables the task requires — the
 * configured window/stride/overlap/gate, the boundary count and each boundary's position — for the
 * evidence log, plus the adversarial `no-motion => zero boundaries` observable.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :sign:testDebugUnitTest --tests '*SignSegmentManualQaProbeTest' -i --rerun-tasks`.
 */
class SignSegmentManualQaProbeTest {

    @Test
    fun `manual QA happy - synthetic motion stream emits N boundaries at expected positions`() = runTest {
        val config = SignSegmenterConfig(windowSize = 10, stride = 10, motionThreshold = 0f)
        val frames = SegmenterFixtures.stream(40) { index ->
            if (index in 10 until 30) SegmenterFixtures.MOTION_STEP * (index - 10) else 0f
        }

        val windows = SignSegmenter(config).segment(SegmenterFixtures.streamOf(frames)).toList()

        println(
            "SIGN-SEGMENT-MANUAL-QA happy config: window=${config.windowSize} stride=${config.stride} " +
                "overlap=${config.overlap} gate=${config.motionThreshold}"
        )
        println("SIGN-SEGMENT-MANUAL-QA happy boundaries=${windows.size}")
        windows.forEach { window ->
            println(
                "SIGN-SEGMENT-MANUAL-QA happy boundary index=${window.index} " +
                    "window=[${window.startMillis},${window.endMillis}]ms score=${window.motionScore}"
            )
        }

        assertEquals(2, windows.size)
    }

    @Test
    fun `manual QA adversarial - a no-motion stream emits zero boundaries`() = runTest {
        val config = SignSegmenterConfig(windowSize = 10, stride = 5, motionThreshold = 0f)

        val windows = SignSegmenter(config)
            .segment(SegmenterFixtures.streamOf(SegmenterFixtures.idleStream(100)))
            .toList()

        println("SIGN-SEGMENT-MANUAL-QA adversarial no-motion: frames=100 boundaries=${windows.size}")
        assertTrue(windows.isEmpty())
    }
}
