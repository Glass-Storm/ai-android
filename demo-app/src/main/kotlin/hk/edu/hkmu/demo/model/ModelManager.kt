@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.model

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local-status strings surfaced by [ModelManager]. */
private const val STATUS_BUNDLED_OK = "bundled (verified on-device)"
private const val STATUS_BUNDLED_MISSING = "bundled (missing from APK)"
private const val STATUS_ABSENT = "absent"
private const val STATUS_PRESENT_OK = "present, sha256 ok"
private const val STATUS_MISMATCH = "present, sha256 MISMATCH"

private const val USER_AGENT = "ai-android-demo/1.0"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val BUFFER_BYTES = 64 * 1024
private const val PROGRESS_STEP_PERCENT = 5
private const val HTTP_MIN_OK = 200
private const val HTTP_MAX_OK = 300
private const val PERCENT = 100

/**
 * Per-model "check & download" policy for the demo.
 *
 * A bundled row reports whether its packaged asset was provisioned to disk. A downloadable row
 * short-circuits when the local file already matches the pinned sha256; otherwise it transfers the
 * artifact to a `.part` file, prints progress every ~5%, verifies the sha256, and only then renames
 * it into place, so a partial or corrupt download never becomes the final artifact.
 *
 * @property root Model root (`filesDir/models`).
 */
class ModelManager(private val root: File) {

    /** Absolute destination of [model] under the model root. */
    fun target(model: DemoModel): File = File(root, model.relativePath)

    /** Local status without touching the network, e.g. `present, sha256 ok`. */
    fun status(model: DemoModel): String {
        val file = target(model)
        return when {
            model.isBundled && file.isFile -> STATUS_BUNDLED_OK
            model.isBundled -> STATUS_BUNDLED_MISSING
            !file.isFile -> STATUS_ABSENT
            Sha256.ofFile(file) == model.sha256 -> STATUS_PRESENT_OK
            else -> STATUS_MISMATCH
        }
    }

    /**
     * Checks the local artifact and, when needed, downloads and verifies it.
     *
     * @return a human-readable transcript of every step.
     */
    suspend fun checkAndDownload(model: DemoModel): String = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        log.appendLine("model: ${model.label}")
        log.appendLine("  path:   ${target(model).path}")
        log.appendLine("  status: ${status(model)}")
        if (!model.isBundled && status(model) != STATUS_PRESENT_OK) {
            download(model, log)
            log.append("  final:  ${status(model)}")
        }
        log.toString().trimEnd()
    }

    private fun download(model: DemoModel, log: StringBuilder) {
        val url = requireNotNull(model.url) { "downloadable model ${model.label} has no url" }
        val expected = requireNotNull(model.sha256) { "downloadable model ${model.label} has no sha256" }
        val target = target(model)
        target.parentFile?.mkdirs()
        val part = File(target.path + PART_SUFFIX)
        part.delete()
        transfer(url, part, log)
        verifyChecksum(part, expected, model.relativePath)
        target.delete()
        if (!part.renameTo(target)) {
            throw IOException("could not move ${part.name} into place")
        }
        log.appendLine("  downloaded ${target.length()} bytes, sha256 ok")
    }

    private fun transfer(url: String, part: File, log: StringBuilder) {
        val connection = openConnection(url)
        try {
            connection.connect()
            val code = connection.responseCode
            if (code < HTTP_MIN_OK || code >= HTTP_MAX_OK) {
                throw IOException("HTTP $code for $url")
            }
            connection.inputStream.use { input -> copyWithProgress(input, part, connection.contentLengthLong, log) }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyChecksum(part: File, expected: String, relativePath: String) {
        val actual = Sha256.ofFile(part)
        if (actual != expected) {
            part.delete()
            throw IOException("sha256 mismatch for $relativePath: expected $expected, got $actual")
        }
    }

    private fun copyWithProgress(input: InputStream, part: File, totalBytes: Long, log: StringBuilder) {
        var nextPercent = PROGRESS_STEP_PERCENT
        part.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            var copied = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    return@use
                }
                output.write(buffer, 0, read)
                copied += read
                nextPercent = reportProgress(copied, totalBytes, nextPercent, log)
            }
        }
    }

    private fun reportProgress(copied: Long, totalBytes: Long, nextPercent: Int, log: StringBuilder): Int {
        if (totalBytes <= 0L) {
            return nextPercent
        }
        val percent = (copied * PERCENT / totalBytes).toInt()
        return if (percent < nextPercent) {
            nextPercent
        } else {
            log.appendLine("  $percent% ($copied/$totalBytes bytes)")
            (percent / PROGRESS_STEP_PERCENT + 1) * PROGRESS_STEP_PERCENT
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}
