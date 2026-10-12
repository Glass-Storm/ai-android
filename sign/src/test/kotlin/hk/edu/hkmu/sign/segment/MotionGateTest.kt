@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.segment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tier-A acceptance for the pure [MotionGate] scoring/gating math. */
class MotionGateTest {

    @Test
    fun `identical frames score exactly zero and moving frames score positive`() {
        val still = listOf(SegmenterFixtures.idle(0), SegmenterFixtures.idle(1), SegmenterFixtures.idle(2))
        val moving = listOf(
            SegmenterFixtures.frame(0, offset = 0f),
            SegmenterFixtures.frame(1, offset = 0.5f),
            SegmenterFixtures.frame(2, offset = 1f)
        )

        assertEquals(0f, MotionGate.score(still), 0f)
        assertTrue(MotionGate.score(moving) > 0f)
    }

    @Test
    fun `the gate opens only strictly above the threshold`() {
        assertFalse(MotionGate.isOpen(score = 0f, threshold = 0f))
        assertTrue(MotionGate.isOpen(score = 0.01f, threshold = 0f))
        assertFalse(MotionGate.isOpen(score = 0.02f, threshold = 0.02f))
        assertTrue(MotionGate.isOpen(score = 0.03f, threshold = 0.02f))
    }
}
