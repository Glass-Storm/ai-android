package hk.edu.hkmu.tts.asset

import java.io.File

/**
 * Narrow file-system seam used by [ModelAssetValidator] so pre-validation is unit-testable without
 * touching a real disk. The default implementation reads the real file system.
 */
fun interface AssetProbe {
    /** Direct children of [directory] (files and sub-directories), or empty when it does not exist. */
    fun children(directory: File): List<File>
}

/** [AssetProbe] backed by `java.io.File`. */
object FileSystemAssetProbe : AssetProbe {
    override fun children(directory: File): List<File> = directory.listFiles()?.toList() ?: emptyList()
}
