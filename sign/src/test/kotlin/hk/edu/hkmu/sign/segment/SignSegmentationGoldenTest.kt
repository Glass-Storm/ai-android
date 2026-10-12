@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.segment

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-file lock for the deterministic transition-gated [SignSegmenter].
 *
 * Segmentation is pure arithmetic over synthetic landmark frames (no model, no device), so the
 * boundary positions for a canonical motion pattern are byte-exact. The scenario and its expected
 * `index/start/end` tuples live in `resources/golden/sign-segmentation.tsv`; a change to the
 * boundary cadence or the motion gate fails here rather than only in an inline expectation.
 */
class SignSegmentationGoldenTest {

    @Test
    fun `the segmenter reproduces the golden window boundaries`() = runTest {
        val scenario = load()
        val frames = SegmenterFixtures.stream(scenario.frameCount) { index ->
            if (index in scenario.motionStart until scenario.motionEnd) {
                SegmenterFixtures.MOTION_STEP * index
            } else {
                0f
            }
        }

        val windows = SignSegmenter(scenario.config).segment(SegmenterFixtures.streamOf(frames)).toList()

        assertEquals("golden window count", scenario.expected.size, windows.size)
        windows.zip(scenario.expected).forEach { (actual, expected) ->
            assertEquals("window index", expected.index, actual.index)
            assertEquals("window start", expected.startMillis, actual.startMillis)
            assertEquals("window end", expected.endMillis, actual.endMillis)
            assertTrue("a golden window must carry motion", actual.motionScore > scenario.config.motionThreshold)
            assertEquals("window length equals the window size", scenario.config.windowSize, actual.frames.size)
        }
    }

    private fun load(): GoldenScenario {
        val text = javaClass.getResourceAsStream(GOLDEN)?.bufferedReader()?.use { it.readText() }
            ?: error("missing golden resource $GOLDEN")
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        val config = lines.first { it.startsWith("config|") }.split('|')
        val windows = lines.filter { it.startsWith("window|") }.map { line ->
            val columns = line.split('|')
            GoldenWindow(columns[1].toInt(), columns[2].toLong(), columns[3].toLong())
        }
        return GoldenScenario(
            config = SignSegmenterConfig(
                windowSize = config[1].toInt(),
                stride = config[2].toInt(),
                motionThreshold = config[3].toFloat()
            ),
            frameCount = config[4].toInt(),
            motionStart = config[5].toInt(),
            motionEnd = config[6].toInt(),
            expected = windows
        )
    }

    private data class GoldenWindow(val index: Int, val startMillis: Long, val endMillis: Long)

    private data class GoldenScenario(
        val config: SignSegmenterConfig,
        val frameCount: Int,
        val motionStart: Int,
        val motionEnd: Int,
        val expected: List<GoldenWindow>
    )

    private companion object {
        const val GOLDEN = "/golden/sign-segmentation.tsv"
    }
}
