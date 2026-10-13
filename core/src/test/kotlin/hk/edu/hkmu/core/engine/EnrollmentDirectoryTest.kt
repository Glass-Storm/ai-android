package hk.edu.hkmu.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Host-JVM coverage of [EnrollmentDirectory]: it maps a voiceprint to a display name via cosine
 * similarity against its threshold, and [EnrollmentDirectory.enroll] is additive (it returns a new
 * directory and never mutates the receiver).
 */
class EnrollmentDirectoryTest {

    private val alice = Voiceprint(floatArrayOf(1f, 0f, 0f))

    @Test
    fun `match resolves an enrolled voiceprint to its display name`() {
        val directory = EnrollmentDirectory().enroll("Alice", alice)

        val match = directory.match(Voiceprint(floatArrayOf(1f, 0f, 0f)))

        assertNotNull(match)
        assertEquals("Alice", match?.displayName)
    }

    @Test
    fun `enroll is additive and leaves the receiver unchanged`() {
        val base = EnrollmentDirectory()

        val withAlice = base.enroll("Alice", alice)

        assertEquals(0, base.size)
        assertEquals(1, withAlice.size)
        assertEquals(0, base.enrollments.size)
        assertEquals("Alice", withAlice.enrollments.single().displayName)
    }

    @Test
    fun `match returns null below the configured threshold`() {
        val directory = EnrollmentDirectory(threshold = 0.9f)
            .enroll("Alice", Voiceprint(floatArrayOf(1f, 0f)))
            .enroll("Bob", Voiceprint(floatArrayOf(0f, 1f)))

        assertNull(directory.match(Voiceprint(floatArrayOf(1f, 1f))))
    }

    @Test
    fun `a lower threshold admits a weak match`() {
        val directory = EnrollmentDirectory(threshold = 0.1f)
            .enroll("Alice", Voiceprint(floatArrayOf(1f, 0f)))

        assertNotNull(directory.match(Voiceprint(floatArrayOf(1f, 5f))))
    }

    @Test
    fun `closest of several enrolled speakers wins`() {
        val directory = EnrollmentDirectory()
            .enroll("Bob", Voiceprint(floatArrayOf(0.5f, 0.866f)))
            .enroll("Alice", Voiceprint(floatArrayOf(1f, 0f)))

        val match = directory.match(Voiceprint(floatArrayOf(1f, 0f)))

        assertEquals("Alice", match?.displayName)
    }

    @Test
    fun `empty directory is exposed and matches nothing`() {
        assertEquals(0, EnrollmentDirectory.Empty.size)
        assertNull(EnrollmentDirectory.Empty.match(alice))
    }

    @Test
    fun `clear keeps the threshold but drops enrollments`() {
        val directory = EnrollmentDirectory(threshold = 0.7f).enroll("Alice", alice)

        val cleared = directory.clear()

        assertEquals(0, cleared.size)
        assertEquals(0.7f, cleared.threshold, 1e-6f)
    }

    @Test
    fun `a non finite threshold is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnrollmentDirectory(threshold = Float.NaN)
        }
    }
}
