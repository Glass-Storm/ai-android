package hk.edu.hkmu.models.store

import hk.edu.hkmu.models.manifest.ModelDescriptor
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/**
 * Filesystem layout and durable partial-state helpers for a download.
 *
 * Everything partial lives next to the final artifact with a `.part` / `.part.meta` suffix so a
 * verified move is a same-volume (atomic) rename and a crash cannot leave a partial file in the
 * final path.
 */
internal object PartialFiles {
    const val PART_SUFFIX = ".part"
    const val META_SUFFIX = ".part.meta"

    private val json = Json { ignoreUnknownKeys = true }

    /** `<root>/<id>/<id>-<version><ext>`. */
    fun finalFile(rootDir: File, descriptor: ModelDescriptor): File =
        File(File(rootDir, descriptor.id), "${descriptor.id}-${descriptor.version.raw}${descriptor.format.extension}")

    fun partFile(finalFile: File): File = File(finalFile.parentFile, finalFile.name + PART_SUFFIX)

    fun metaFile(finalFile: File): File = File(finalFile.parentFile, finalFile.name + META_SUFFIX)

    /**
     * Resolves the resume offset for [partFile], deleting an inconsistent partial and its sidecar.
     *
     * A partial is only resumable when its [PartialState] matches the descriptor exactly; anything
     * else (missing/corrupt sidecar, wrong model/sha/size, oversize) is treated as corrupt and
     * cleaned up so the next attempt restarts from zero.
     */
    fun resumeOffset(descriptor: ModelDescriptor, partFile: File, metaFile: File): Long {
        val length = if (partFile.isFile) partFile.length() else 0L
        if (length in 1..descriptor.sizeBytes && stateMatches(readState(metaFile), descriptor)) {
            return length
        }
        cleanup(partFile, metaFile)
        return 0L
    }

    private fun stateMatches(state: PartialState?, descriptor: ModelDescriptor): Boolean {
        if (state == null) return false
        return state.modelId == descriptor.id &&
            state.sha256 == descriptor.sha256 &&
            state.sizeBytes == descriptor.sizeBytes
    }

    fun readState(metaFile: File): PartialState? {
        if (!metaFile.isFile) return null
        return try {
            json.decodeFromString(PartialState.serializer(), metaFile.readText())
        } catch (_: IOException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }
    }

    fun writeState(metaFile: File, state: PartialState) {
        metaFile.parentFile?.mkdirs()
        metaFile.writeText(json.encodeToString(PartialState.serializer(), state))
    }

    /** Deletes both the partial artifact and its sidecar. */
    fun cleanup(partFile: File, metaFile: File) {
        deleteQuietly(partFile)
        deleteQuietly(metaFile)
    }

    fun deleteQuietly(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Moves [source] onto [target] with a best-effort atomic rename, falling back to a plain
     * replace when the filesystem cannot do an atomic move.
     */
    fun atomicMove(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
