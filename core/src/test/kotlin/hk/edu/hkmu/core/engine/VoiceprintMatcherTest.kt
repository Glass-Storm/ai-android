@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Host-JVM coverage of [VoiceprintMatcher]: the cosine similarity is a pure function of the vectors,
 * the threshold decides a match, and a degenerate or mismatched vector never matches.
 */
class VoiceprintMatcherTest {

    @Test
    fun `identical vectors have cosine similarity one`() {
        val vector = floatArrayOf(0.2f, -0.5f, 0.8f)

        assertEquals(1f, VoiceprintMatcher.cosineSimilarity(vector, vector.copyOf()), TOLERANCE)
    }

    @Test
    fun `orthogonal vectors have cosine similarity zero`() {
        assertEquals(0f, VoiceprintMatcher.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), TOLERANCE)
    }

    @Test
    fun `opposite vectors have cosine similarity minus one`() {
        assertEquals(-1f, VoiceprintMatcher.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f)), TOLERANCE)
    }

    @Test
    fun `similarity ignores vector magnitude`() {
        val scaled = VoiceprintMatcher.cosineSimilarity(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(10f, 20f, 30f)
        )

        assertEquals(1f, scaled, TOLERANCE)
    }

    @Test
    fun `a zero-magnitude vector never matches`() {
        assertEquals(0f, VoiceprintMatcher.cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), TOLERANCE)
        assertNull(
            "a zero vector must not reach a positive threshold",
            VoiceprintMatcher.bestMatch(Voiceprint(floatArrayOf(0f, 0f)), listOf(enroll("A", 1f, 1f)))
        )
    }

    @Test
    fun `mismatched dimensions raise a typed shape error`() {
        val exception = assertThrows(AiAndroidException.InputShapeMismatch::class.java) {
            VoiceprintMatcher.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f))
        }

        assertEquals("voiceprint dim 2", exception.expected)
        assertEquals("voiceprint dim 3", exception.actual)
    }

    @Test
    fun `best match returns the most similar enrollment above threshold`() {
        val query = Voiceprint(floatArrayOf(1f, 0f))
        val enrollments = listOf(
            enroll("Bob", 0.6f, 0.8f),
            enroll("Alice", 1f, 0f),
            enroll("Carol", 0.8f, 0.6f)
        )

        val match = VoiceprintMatcher.bestMatch(query, enrollments, threshold = 0.5f)

        assertEquals("Alice", match?.displayName)
        assertEquals(1f, match?.similarity ?: 0f, TOLERANCE)
    }

    @Test
    fun `best match returns null when nothing reaches the threshold`() {
        val query = Voiceprint(floatArrayOf(1f, 0f))

        val match = VoiceprintMatcher.bestMatch(query, listOf(enroll("Bob", 0.2f, 0.98f)), threshold = 0.5f)

        assertNull(match)
    }

    @Test
    fun `ties break in favour of the earliest enrollment`() {
        val query = Voiceprint(floatArrayOf(1f, 0f))
        val enrollments = listOf(
            enroll("First", 1f, 0f),
            enroll("Second", 1f, 0f)
        )

        val match = VoiceprintMatcher.bestMatch(query, enrollments, threshold = 0.5f)

        assertEquals("First", match?.displayName)
    }

    @Test
    fun `an empty enrollment set yields no match`() {
        assertNull(VoiceprintMatcher.bestMatch(Voiceprint(floatArrayOf(1f, 0f)), emptyList()))
    }

    private fun enroll(name: String, x: Float, y: Float) = SpeakerEnrollment(name, Voiceprint(floatArrayOf(x, y)))

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
