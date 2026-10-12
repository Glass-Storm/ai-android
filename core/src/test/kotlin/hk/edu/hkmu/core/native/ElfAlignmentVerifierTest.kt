@file:Suppress("MagicNumber")

package hk.edu.hkmu.core.native

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Locks the pure-JVM ELF reader: it parses ELF32/ELF64 (both endiannesses), reports each `PT_LOAD`
 * `p_align`, and treats `0x4000` as 16 KB-compliant. Also audits the vendored sherpa-onnx AAR's real
 * arm64 `.so` files end-to-end.
 */
class ElfAlignmentVerifierTest {

    @Test
    fun `elf64 with 16 KB load alignment is compliant`() {
        val report = ElfAlignmentVerifier.verify(elf64(ph64(PT_LOAD, align = PAGE_16KB)), "compliant64")

        assertTrue(report.is64Bit)
        assertTrue(report.littleEndian)
        assertEquals(1, report.segments.size)
        assertEquals(PAGE_16KB, report.segments.single().align)
        assertTrue(report.all16KbAligned)
        assertTrue(report.misalignedSegments().isEmpty())
    }

    @Test
    fun `elf64 with 4 KB load alignment is not compliant`() {
        val report = ElfAlignmentVerifier.verify(elf64(ph64(PT_LOAD, align = PAGE_4KB)), "legacy64")

        assertFalse(report.all16KbAligned)
        assertEquals(1, report.misalignedSegments().size)
        assertEquals(PAGE_4KB, report.misalignedSegments().single().align)
    }

    @Test
    fun `non-load segments are ignored and only PT_LOAD alignment is reported`() {
        val report = ElfAlignmentVerifier.verify(
            elf64(ph64(PT_LOAD, align = PAGE_16KB), ph64(PT_NOTE, align = PAGE_4KB)),
            "mixed"
        )

        assertEquals("only the PT_LOAD segment counts", 1, report.segments.size)
        assertTrue(report.all16KbAligned)
    }

    @Test
    fun `elf32 with 16 KB load alignment is compliant`() {
        val report = ElfAlignmentVerifier.verify(elf32(ph32(PT_LOAD, align = PAGE_16KB)), "compliant32")

        assertFalse(report.is64Bit)
        assertTrue(report.all16KbAligned)
        assertEquals(PAGE_16KB, report.segments.single().align)
    }

    @Test
    fun `big-endian elf64 is parsed`() {
        val report = ElfAlignmentVerifier.verify(
            elf64(ph64(PT_LOAD, align = PAGE_16KB), littleEndian = false),
            "compliant64be"
        )

        assertTrue(report.is64Bit)
        assertFalse(report.littleEndian)
        assertTrue(report.all16KbAligned)
    }

    @Test
    fun `invalid magic raises a typed format error`() {
        val notElf = ByteArray(ELF64_HEADER_SIZE) { 'X'.code.toByte() }

        assertThrows(ElfFormatException::class.java) { ElfAlignmentVerifier.verify(notElf, "garbage") }
    }

    @Test
    fun `truncated input raises a typed format error`() {
        val truncated = byteArrayOf(ELF_MAGIC.toByte(), 'E'.code.toByte(), 'L'.code.toByte())

        assertThrows(ElfFormatException::class.java) { ElfAlignmentVerifier.verify(truncated, "tiny") }
    }

    @Test
    fun `every vendored arm64 native library in the AAR is 16 KB aligned`() {
        val aar = findVendoredAar()
        assumeTrue("vendored sherpa-onnx AAR not present; skipping real-.so audit", aar != null)

        ZipFile(aar!!).use { zip ->
            val arm64Libraries = zip.entries().asSequence()
                .filter { it.name.startsWith(ARM64_PREFIX) && it.name.endsWith(".so") }
                .toList()
            assertTrue("expected at least one arm64 .so in the AAR", arm64Libraries.isNotEmpty())

            arm64Libraries.forEach { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val report = ElfAlignmentVerifier.verify(bytes, entry.name)
                assertTrue(
                    "${entry.name} must be 16 KB aligned but had aligns=${report.segments.map { it.align }}",
                    report.all16KbAligned
                )
            }
        }
    }

    private fun findVendoredAar(): File? = AAR_CANDIDATES.map(::File).firstOrNull { it.isFile }

    private data class ProgramHeader64(val type: Int, val offset: Long, val vaddr: Long, val align: Long)

    private data class ProgramHeader32(val type: Int, val offset: Long, val vaddr: Long, val align: Long)

    private fun ph64(type: Int, align: Long, offset: Long = 0, vaddr: Long = 0) =
        ProgramHeader64(type, offset, vaddr, align)

    private fun ph32(type: Int, align: Long, offset: Long = 0, vaddr: Long = 0) =
        ProgramHeader32(type, offset, vaddr, align)

    private fun elf64(vararg headers: ProgramHeader64, littleEndian: Boolean = true): ByteArray {
        val bytes = ByteArray(ELF64_HEADER_SIZE + headers.size * ELF64_PH_SIZE)
        writeIdent(bytes, CLASS_64, littleEndian)
        put(bytes, E_PHOFF_64, ELF64_HEADER_SIZE.toLong(), LONG_WIDTH, littleEndian)
        put(bytes, E_PHENTSIZE_64, ELF64_PH_SIZE.toLong(), SHORT_WIDTH, littleEndian)
        put(bytes, E_PHNUM_64, headers.size.toLong(), SHORT_WIDTH, littleEndian)
        headers.forEachIndexed { index, segment ->
            val base = ELF64_HEADER_SIZE + index * ELF64_PH_SIZE
            put(bytes, base, segment.type.toLong(), INT_WIDTH, littleEndian)
            put(bytes, base + PH64_OFFSET, segment.offset, LONG_WIDTH, littleEndian)
            put(bytes, base + PH64_VADDR, segment.vaddr, LONG_WIDTH, littleEndian)
            put(bytes, base + PH64_ALIGN, segment.align, LONG_WIDTH, littleEndian)
        }
        return bytes
    }

    private fun elf32(vararg headers: ProgramHeader32): ByteArray {
        val bytes = ByteArray(ELF32_HEADER_SIZE + headers.size * ELF32_PH_SIZE)
        writeIdent(bytes, CLASS_32, littleEndian = true)
        put(bytes, E_PHOFF_32, ELF32_HEADER_SIZE.toLong(), INT_WIDTH, littleEndian = true)
        put(bytes, E_PHENTSIZE_32, ELF32_PH_SIZE.toLong(), SHORT_WIDTH, littleEndian = true)
        put(bytes, E_PHNUM_32, headers.size.toLong(), SHORT_WIDTH, littleEndian = true)
        headers.forEachIndexed { index, header ->
            val base = ELF32_HEADER_SIZE + index * ELF32_PH_SIZE
            put(bytes, base, header.type.toLong(), INT_WIDTH, littleEndian = true)
            put(bytes, base + PH32_OFFSET, header.offset, INT_WIDTH, littleEndian = true)
            put(bytes, base + PH32_VADDR, header.vaddr, INT_WIDTH, littleEndian = true)
            put(bytes, base + PH32_ALIGN, header.align, INT_WIDTH, littleEndian = true)
        }
        return bytes
    }

    private fun writeIdent(bytes: ByteArray, elfClass: Int, littleEndian: Boolean) {
        bytes[0] = ELF_MAGIC.toByte()
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = elfClass.toByte()
        bytes[5] = if (littleEndian) DATA_LE.toByte() else DATA_BE.toByte()
    }

    private fun put(bytes: ByteArray, offset: Int, value: Long, width: Int, littleEndian: Boolean) {
        repeat(width) { i ->
            val shift = if (littleEndian) i * BITS_PER_BYTE else (width - 1 - i) * BITS_PER_BYTE
            bytes[offset + i] = ((value ushr shift) and BYTE_MASK.toLong()).toByte()
        }
    }

    private companion object {
        const val ELF_MAGIC = 0x7F
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
        const val SHORT_WIDTH = 2
        const val INT_WIDTH = 4
        const val LONG_WIDTH = 8

        const val CLASS_32 = 1
        const val CLASS_64 = 2
        const val DATA_LE = 1
        const val DATA_BE = 2
        const val PT_LOAD = 1
        const val PT_NOTE = 4

        const val PAGE_16KB = 0x4000L
        const val PAGE_4KB = 0x1000L

        const val ELF64_HEADER_SIZE = 64
        const val ELF64_PH_SIZE = 56
        const val ELF32_HEADER_SIZE = 52
        const val ELF32_PH_SIZE = 32

        const val E_PHOFF_64 = 0x20
        const val E_PHENTSIZE_64 = 0x36
        const val E_PHNUM_64 = 0x38
        const val E_PHOFF_32 = 0x1C
        const val E_PHENTSIZE_32 = 0x2A
        const val E_PHNUM_32 = 0x2C

        const val PH64_OFFSET = 8
        const val PH64_VADDR = 16
        const val PH64_ALIGN = 48
        const val PH32_OFFSET = 4
        const val PH32_VADDR = 8
        const val PH32_ALIGN = 28

        const val ARM64_PREFIX = "jni/arm64-v8a/"
        const val AAR_NAME = "sherpa-onnx-1.13.8.aar"
        val AAR_CANDIDATES = listOf("../libs/$AAR_NAME", "libs/$AAR_NAME")
    }
}
