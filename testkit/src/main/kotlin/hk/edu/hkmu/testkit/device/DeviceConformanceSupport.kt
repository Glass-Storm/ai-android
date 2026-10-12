package hk.edu.hkmu.testkit.device

import hk.edu.hkmu.core.native.ElfAlignmentVerifier
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * One native library entry found inside a packaged APK/AAR and its 16 KB alignment verdict.
 *
 * @property entry Archive path of the `.so` (e.g. `lib/arm64-v8a/libsherpa-onnx-jni.so`).
 * @property abi Android ABI taken from the entry path.
 * @property all16KbAligned True when every `PT_LOAD` segment is `p_align == 0x4000`.
 * @property loadSegmentCount Number of `PT_LOAD` segments parsed from the ELF image.
 * @property minPageAlignHex Smallest `p_align` across the load segments, as `0x…` (`none` if absent).
 */
data class NativeLibraryAlignment(
    val entry: String,
    val abi: String,
    val all16KbAligned: Boolean,
    val loadSegmentCount: Int,
    val minPageAlignHex: String
)

/**
 * Pure-JVM helpers shared by the Tier B instrumented suites.
 *
 * Native alignment is verified with the shipped [ElfAlignmentVerifier] (the same parser the packaging
 * gate uses), so the on-device check and the build-time check cannot drift apart. Reading the ELF
 * images out of the installed archive means the assertion reflects **exactly what the device
 * loaded**, not a separate copy on the build host.
 */
object DeviceConformanceSupport {

    private const val NATIVE_PREFIX = "lib/"
    private const val NATIVE_SUFFIX = ".so"
    private const val HEX_RADIX = 16

    /**
     * Names of [required] files missing from [dir].
     *
     * An empty list means the directory is fully provisioned. A missing directory reports every
     * required name, so a caller can turn the result straight into an `Assume.assumeTrue` skip.
     */
    fun missingProvisioned(dir: File, required: List<String>): List<String> {
        if (!dir.isDirectory) {
            return required
        }
        return required.filterNot { name -> File(dir, name).exists() }
    }

    /**
     * Scans every `lib/<abi>/<name>.so` entry inside [archivePath] (an APK or AAR) and reports its 16 KB
     * alignment. Returns an empty list when the archive carries no native library.
     */
    fun scanNativeLibraries(archivePath: String): List<NativeLibraryAlignment> {
        val archive = File(archivePath)
        if (!archive.isFile) {
            return emptyList()
        }
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> isNativeLibrary(entry) }
                .map { entry -> alignmentOf(zip, entry.name) }
                .sortedBy { it.entry }
                .toList()
        }
    }

    private fun isNativeLibrary(entry: ZipEntry): Boolean =
        !entry.isDirectory && entry.name.startsWith(NATIVE_PREFIX) && entry.name.endsWith(NATIVE_SUFFIX)

    private fun alignmentOf(zip: ZipFile, entryName: String): NativeLibraryAlignment {
        val entry = zip.getEntry(entryName)
        val bytes = zip.getInputStream(entry).use { stream -> stream.readBytes() }
        val report = ElfAlignmentVerifier.verify(bytes, entryName)
        val minAlign = report.segments.minOfOrNull { segment -> segment.align }
        return NativeLibraryAlignment(
            entry = entryName,
            abi = entryName.removePrefix(NATIVE_PREFIX).substringBefore('/'),
            all16KbAligned = report.all16KbAligned,
            loadSegmentCount = report.segments.size,
            minPageAlignHex = if (minAlign == null) "none" else "0x" + minAlign.toString(HEX_RADIX)
        )
    }
}
