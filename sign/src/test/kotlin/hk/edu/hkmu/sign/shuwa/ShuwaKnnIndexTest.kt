package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the KNN table: header parsing, float16 decoding and the match decision. The file
 * is built in-test, so the parser is proven without shipping any model data.
 */
class ShuwaKnnIndexTest {

    @Test
    fun `float16 decoding matches IEEE-754`() {
        assertEquals(0f, ShuwaKnnIndex.halfToFloat(0x0000), 0f)
        assertEquals(1f, ShuwaKnnIndex.halfToFloat(0x3C00), 0f)
        assertEquals(-2f, ShuwaKnnIndex.halfToFloat(0xC000), 0f)
        assertEquals(0.5f, ShuwaKnnIndex.halfToFloat(0x3800), 0f)
    }

    @Test
    fun `a loaded table exposes its labels and picks the nearest row`() {
        val index = ShuwaKnnIndex.load(table(prefix = "Hksl_"))

        assertEquals(listOf("Hksl_able_to", "Hksl_bathroom"), index.labels)
        assertEquals(2, index.size)
        assertEquals(3, index.dimensions)

        val match = index.match(floatArrayOf(1f, 0f, 0f))
        assertEquals("Hksl_able_to", match.label)
        assertTrue("confidence should be high", match.confidence > 0.9f)
        assertEquals(0f, match.distance, 1e-3f)
    }

    @Test
    fun `the second row wins when the embedding is nearer to it`() {
        val index = ShuwaKnnIndex.load(table(prefix = "Hksl_"))

        val match = index.match(floatArrayOf(0f, 1f, 0f))

        assertEquals("Hksl_bathroom", match.label)
    }

    @Test
    fun `an embedding of the wrong size is rejected`() {
        val index = ShuwaKnnIndex.load(table(prefix = "Hksl_"))

        assertThrows(IllegalArgumentException::class.java) {
            index.match(floatArrayOf(1f, 0f))
        }
    }

    @Test
    fun `a non-commercial table is rejected for commercial use`() {
        val spec =
            ShuwaModelSpec(
                license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
            )

        assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            ShuwaKnnIndex.load(
                file = table(prefix = "Hksl_"),
                spec = spec,
                licensePolicy = DefaultLicensePolicy(),
                intent = UsageIntent.Commercial
            )
        }
    }

    /** Builds a minimal `SKS1` table: two labels, two rows of three float16 values. */
    private fun table(prefix: String): File {
        val labels = listOf("${prefix}able_to", "${prefix}bathroom")
        val buffer = ByteBuffer.allocate(BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC)
        buffer.putShort(labels.size.toShort())
        buffer.putInt(ROWS)
        buffer.putShort(DIMENSIONS.toShort())
        labels.forEach { label ->
            val raw = label.toByteArray(Charsets.UTF_8)
            buffer.putShort(raw.size.toShort())
            buffer.put(raw)
        }
        repeat(ROWS) { row -> buffer.putShort(row.toShort()) }
        // Row 0 = (1, 0, 0), row 1 = (0, 1, 0).
        buffer.putShort(HALF_ONE)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ZERO)
        buffer.putShort(HALF_ONE)
        buffer.putShort(HALF_ZERO)

        val file = File.createTempFile("shuwa-knn", ".bin")
        file.deleteOnExit()
        file.writeBytes(buffer.array().copyOf(buffer.position()))
        return file
    }

    private companion object {
        val MAGIC: ByteArray = "SKS1".toByteArray(Charsets.US_ASCII)
        const val BUFFER_BYTES = 256
        const val ROWS = 2
        const val DIMENSIONS = 3
        const val HALF_ZERO: Short = 0x0000
        const val HALF_ONE: Short = 0x3C00
    }
}
