package hk.edu.hkmu.models.store

import java.io.File

/**
 * Reports the bytes available on the volume holding `directory`.
 *
 * A seam so the disk-space preflight can be exercised without filling a real volume. Production
 * uses [Real]; tests inject a probe that returns a fixed (tiny) value.
 */
fun interface DiskSpaceProbe {
    /** @return usable bytes on the volume containing `directory`. */
    fun usableBytes(directory: File): Long

    companion object {
        /** The real probe, backed by [File.getUsableSpace]. */
        val Real: DiskSpaceProbe = DiskSpaceProbe { directory -> directory.usableSpace }
    }
}
