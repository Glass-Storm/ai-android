@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.device

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Host-tier checks for the 16 KB APK/AAR scanning helper used by the Tier B device suites. */
class DeviceConformanceSupportTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `scans a 16 KB aligned native library out of an archive`() {
        val archive = temporaryFolder.newFile("libs.aar")
        writeArchive(archive, "lib/arm64-v8a/libtest.so", elf64WithLoadAlign(PAGE_16KB))
        val report = DeviceConformanceSupport.scanNativeLibraries(archive.absolutePath)
        assertEquals(1, report.size)
        val entry = report.single()
        assertEquals("lib/arm64-v8a/libtest.so", entry.entry)
        assertEquals("arm64-v8a", entry.abi)
        assertTrue(entry.all16KbAligned)
        assertEquals("0x4000", entry.minPageAlignHex)
    }

    @Test
    fun `reports a 4 KB aligned native library as misaligned`() {
        val archive = temporaryFolder.newFile("libs.aar")
        writeArchive(archive, "lib/x86_64/libold.so", elf64WithLoadAlign(PAGE_4KB))
        val report = DeviceConformanceSupport.scanNativeLibraries(archive.absolutePath)
        assertEquals(1, report.size)
        assertFalse(report.single().all16KbAligned)
        assertEquals("0x1000", report.single().minPageAlignHex)
    }

    @Test
    fun `returns an empty list for a missing archive and a library-free archive`() {
        val empty = temporaryFolder.newFile("empty.aar")
        writeArchive(empty, "classes.jar", byteArrayOf(0x50, 0x4B))
        assertEquals(emptyList<NativeLibraryAlignment>(), DeviceConformanceSupport.scanNativeLibraries(empty.path))
        assertEquals(
            emptyList<NativeLibraryAlignment>(),
            DeviceConformanceSupport.scanNativeLibraries(File(temporaryFolder.root, "absent.aar").path)
        )
    }

    @Test
    fun `missingProvisioned lists every absent required name`() {
        val dir = temporaryFolder.newFolder("models")
        File(dir, "present.txt").writeText("x")
        val missing = DeviceConformanceSupport.missingProvisioned(dir, listOf("present.txt", "absent.onnx"))
        assertEquals(listOf("absent.onnx"), missing)
    }

    @Test
    fun `missingProvisioned reports all names for an absent directory`() {
        val absent = File(temporaryFolder.root, "nope")
        val required = listOf("a", "b")
        assertEquals(required, DeviceConformanceSupport.missingProvisioned(absent, required))
    }

    private fun writeArchive(archive: File, entryName: String, bytes: ByteArray) {
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    /** Minimal LE ELF64 (one `PT_LOAD`) — enough for the production parser to read `p_align`. */
    private fun elf64WithLoadAlign(align: Long): ByteArray {
        val buffer = ByteBuffer.allocate(ELF64_HEADER_BYTES + PROGRAM_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ELF_MAGIC)
        buffer.put(ELF_CLASS_64)
        buffer.put(ELF_DATA_LE)
        buffer.put(ELF_VERSION)
        repeat(IDENT_PAD_BYTES) { buffer.put(0) }
        buffer.putShort(ET_EXEC)
        buffer.putShort(AARCH64_MACHINE)
        buffer.putInt(EV_CURRENT)
        buffer.putLong(0)
        buffer.putLong(ELF64_HEADER_BYTES.toLong())
        buffer.putLong(0)
        buffer.putInt(0)
        buffer.putShort(ELF64_HEADER_BYTES.toShort())
        buffer.putShort(PROGRAM_HEADER_BYTES.toShort())
        buffer.putShort(1)
        repeat(3) { buffer.putShort(0) }
        buffer.putInt(PT_LOAD)
        buffer.putInt(0)
        repeat(5) { buffer.putLong(0) }
        buffer.putLong(align)
        return buffer.array()
    }

    private companion object {
        const val PAGE_16KB = 0x4000L
        const val PAGE_4KB = 0x1000L
        const val ELF64_HEADER_BYTES = 64
        const val PROGRAM_HEADER_BYTES = 56
        const val IDENT_PAD_BYTES = 9
        const val ET_EXEC: Short = 2
        const val AARCH64_MACHINE: Short = 183
        const val EV_CURRENT = 1
        const val PT_LOAD = 1
        const val ELF_VERSION: Byte = 1
        const val ELF_CLASS_64: Byte = 2
        const val ELF_DATA_LE: Byte = 1
        val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    }
}
