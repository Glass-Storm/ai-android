package hk.edu.hkmu.core.native

import java.io.File

/** Raised when a byte stream is not a well-formed ELF object. */
class ElfFormatException(message: String) : IllegalArgumentException(message)

/** One ELF program header whose `p_type` is `PT_LOAD`. */
data class ElfLoadSegment(
    val index: Int,
    val offset: Long,
    val virtualAddress: Long,
    val align: Long
) {
    /** True when the segment is aligned to the 16 KB Android page size required from SDK 35. */
    val is16KbAligned: Boolean get() = align == ElfAlignmentVerifier.PAGE_16KB
}

/** Result of scanning an ELF file's `PT_LOAD` segments for 16 KB page alignment. */
data class ElfAlignmentReport(
    val source: String,
    val is64Bit: Boolean,
    val littleEndian: Boolean,
    val segments: List<ElfLoadSegment>
) {
    /** True when there is at least one `PT_LOAD` segment and every one is 16 KB-aligned. */
    val all16KbAligned: Boolean get() = segments.isNotEmpty() && segments.all { it.is16KbAligned }

    /** The `PT_LOAD` segments that are NOT 16 KB-aligned (empty means compliant). */
    fun misalignedSegments(): List<ElfLoadSegment> = segments.filterNot { it.is16KbAligned }
}

/**
 * Pure-JVM ELF reader that reports the `p_align` of every `PT_LOAD` segment so the Android 16 KB
 * page-size requirement (`p_align == 0x4000`) can be asserted without Android SDK or NDK tooling.
 *
 * Supports ELF32/ELF64 in both little- and big-endian encodings. Reused by the packaging gate
 * (todo 30) to audit every vendored `.so`.
 */
object ElfAlignmentVerifier {
    /** Required 16 KB Android load alignment (`p_align`). */
    const val PAGE_16KB: Long = 0x4000L

    private const val BYTE_MASK = 0xFF

    private const val ELF_MAGIC = 0x7F
    private const val ELF_IDENT_SIZE = 16
    private const val IDENT_CLASS = 4
    private const val IDENT_DATA = 5
    private const val IDENT_F = 3
    private const val CLASS_32 = 1
    private const val CLASS_64 = 2
    private const val DATA_LE = 1
    private const val DATA_BE = 2
    private const val PT_LOAD = 1L

    private const val PHOFF_32 = 0x1C
    private const val PHOFF_64 = 0x20
    private const val PHENTSIZE_32 = 0x2A
    private const val PHENTSIZE_64 = 0x36
    private const val PHNUM_32 = 0x2C
    private const val PHNUM_64 = 0x38

    private const val PH_OFFSET_32 = 4
    private const val PH_OFFSET_64 = 8
    private const val PH_VADDR_32 = 8
    private const val PH_VADDR_64 = 16
    private const val PH_ALIGN_32 = 28
    private const val PH_ALIGN_64 = 48

    /** Verifies an ELF file on disk. */
    fun verify(path: String): ElfAlignmentReport = verify(File(path).readBytes(), path)

    /** Verifies an in-memory ELF image. */
    fun verify(bytes: ByteArray, source: String = "memory"): ElfAlignmentReport {
        requireElfIdent(bytes, source)
        val classByte = bytes[IDENT_CLASS].toInt()
        val is64Bit = when (classByte) {
            CLASS_64 -> true
            CLASS_32 -> false
            else -> throw ElfFormatException("unsupported ELF class $classByte in $source")
        }
        val littleEndian = when (bytes[IDENT_DATA].toInt()) {
            DATA_LE -> true
            DATA_BE -> false
            else -> throw ElfFormatException("unsupported ELF data encoding in $source")
        }
        val reader = ElfReader(bytes, littleEndian)
        val phoff = (if (is64Bit) reader.u64(PHOFF_64) else reader.u32(PHOFF_32)).toInt()
        val phentsize = if (is64Bit) reader.u16(PHENTSIZE_64) else reader.u16(PHENTSIZE_32)
        val phnum = if (is64Bit) reader.u16(PHNUM_64) else reader.u16(PHNUM_32)
        val layout = HeaderLayout(is64Bit, phoff, phentsize, phnum)
        return ElfAlignmentReport(source, is64Bit, littleEndian, readLoadSegments(reader, layout, source))
    }

    private fun requireElfIdent(bytes: ByteArray, source: String) {
        val isElf = bytes.size >= ELF_IDENT_SIZE &&
            (bytes[0].toInt() and BYTE_MASK) == ELF_MAGIC &&
            bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() &&
            bytes[IDENT_F] == 'F'.code.toByte()
        if (!isElf) throw ElfFormatException("not an ELF file: $source")
    }

    private fun readLoadSegments(reader: ElfReader, layout: HeaderLayout, source: String): List<ElfLoadSegment> {
        if (layout.phentsize <= 0) return emptyList()
        return buildList {
            repeat(layout.phnum) { i ->
                val base = layout.phoff + i * layout.phentsize
                if (base < 0 || base + layout.phentsize > reader.size) {
                    throw ElfFormatException("program header $i out of bounds in $source")
                }
                if (reader.u32(base) == PT_LOAD) {
                    val align =
                        if (layout.is64Bit) reader.u64(base + PH_ALIGN_64) else reader.u32(base + PH_ALIGN_32)
                    val offset =
                        if (layout.is64Bit) reader.u64(base + PH_OFFSET_64) else reader.u32(base + PH_OFFSET_32)
                    val vaddr =
                        if (layout.is64Bit) reader.u64(base + PH_VADDR_64) else reader.u32(base + PH_VADDR_32)
                    add(ElfLoadSegment(i, offset, vaddr, align))
                }
            }
        }
    }

    private class HeaderLayout(
        val is64Bit: Boolean,
        val phoff: Int,
        val phentsize: Int,
        val phnum: Int
    )

    /** Minimal endianness-aware reader over an in-memory ELF image. */
    private class ElfReader(
        private val bytes: ByteArray,
        private val littleEndian: Boolean
    ) {
        val size: Int get() = bytes.size

        fun u8(offset: Int): Int = bytes[offset].toInt() and BYTE_MASK

        fun u16(offset: Int): Int = readUnsigned(offset, Short.SIZE_BYTES).toInt()

        fun u32(offset: Int): Long = readUnsigned(offset, Int.SIZE_BYTES)

        fun u64(offset: Int): Long = readUnsigned(offset, Long.SIZE_BYTES)

        private fun readUnsigned(offset: Int, width: Int): Long {
            var value = 0L
            repeat(width) { i ->
                val byte = u8(offset + i).toLong()
                value = if (littleEndian) {
                    value or (byte shl (i * Byte.SIZE_BITS))
                } else {
                    (value shl Byte.SIZE_BITS) or byte
                }
            }
            return value
        }
    }
}
