package hk.edu.hkmu.models.store

import hk.edu.hkmu.models.manifest.ModelDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Fetches one descriptor's artifact into [partFile] by walking the descriptor's mirrors in order.
 *
 * Range resume is used whenever a resumable partial already exists; `206` appends, a `200` that
 * ignores the range restarts from zero, `416` means the partial is already complete (or must be
 * reset), and `5xx`/I/O/timeout failures fall over to the next mirror while keeping the partial so
 * the next mirror — or a later restart — can resume.
 *
 * The class is constructed per download and returns normally only once [partFile] holds exactly
 * [ModelDescriptor.sizeBytes] bytes. Checksum verification is the caller's job (it is the only place
 * that must decide between a terminal [hk.edu.hkmu.core.error.AiAndroidException.ChecksumMismatch]
 * and a mirror failure).
 */
internal class MirrorDownloader(
    private val descriptor: ModelDescriptor,
    private val partFile: File,
    private val metaFile: File,
    private val connectionFactory: HttpConnectionFactory,
    private val config: ModelStoreConfig
) {

    @Suppress("TooGenericExceptionCaught")
    suspend fun fetch(emit: suspend (DownloadProgress) -> Unit) {
        val failures = mutableListOf<MirrorFailure>()
        var attempt = 0
        var index = 0
        while (index < descriptor.urls.size && length() < descriptor.sizeBytes) {
            val url = descriptor.urls[index]
            attempt += 1
            try {
                attemptMirror(url, attempt, emit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                failures += MirrorFailure(url, describe(error))
            }
            index += 1
        }

        val finalLength = length()
        if (finalLength != descriptor.sizeBytes) {
            if (finalLength > descriptor.sizeBytes) {
                PartialFiles.cleanup(partFile, metaFile)
            }
            throw DownloadException.MirrorExhausted(descriptor.id, failures)
        }
    }

    private suspend fun attemptMirror(url: String, attempt: Int, emit: suspend (DownloadProgress) -> Unit) {
        val offset = length()
        val connection = connectionFactory.open(url, offset)
        try {
            when (val status = connection.responseCode) {
                HttpConnectionFactory.STATUS_PARTIAL -> {
                    requireContentRangeStart(connection, offset, url)
                    receive(connection, url, offset, attempt, emit)
                }
                HttpConnectionFactory.STATUS_OK -> {
                    val start = if (offset > 0L) {
                        PartialFiles.deleteQuietly(metaFile)
                        PartialFiles.deleteQuietly(partFile)
                        0L
                    } else {
                        0L
                    }
                    receive(connection, url, start, attempt, emit)
                }
                HttpConnectionFactory.STATUS_RANGE_NOT_SATISFIABLE -> {
                    if (length() < descriptor.sizeBytes) {
                        PartialFiles.cleanup(partFile, metaFile)
                    }
                }
                else -> throw IOException("HTTP $status from $url")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun receive(
        connection: HttpURLConnection,
        url: String,
        start: Long,
        attempt: Int,
        emit: suspend (DownloadProgress) -> Unit
    ) {
        partFile.parentFile?.mkdirs()
        PartialFiles.writeState(metaFile, PartialState(descriptor.id, descriptor.sha256, descriptor.sizeBytes, url))
        val append = start > 0L && partFile.isFile && partFile.length() == start
        val output = FileOutputStream(partFile, append)
        val input = connection.inputStream
        coroutineScope {
            val closer = launch(Dispatchers.IO) {
                try {
                    awaitCancellation()
                } finally {
                    runCatching { input.close() }
                    runCatching { connection.disconnect() }
                }
            }
            try {
                streamBody(input, output, if (append) start else 0L, url, attempt, emit)
            } finally {
                closer.cancel()
                runCatching { output.close() }
                runCatching { input.close() }
            }
        }
        verifyLength(url)
    }

    @Suppress("LongParameterList")
    private suspend fun streamBody(
        input: InputStream,
        output: OutputStream,
        start: Long,
        url: String,
        attempt: Int,
        emit: suspend (DownloadProgress) -> Unit
    ) {
        val buffer = ByteArray(config.copyBufferBytes)
        emit(DownloadProgress(descriptor.id, DownloadPhase.Downloading, start, descriptor.sizeBytes, url, attempt))
        var total = start
        var lastEmitted = total
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            if (total - lastEmitted >= config.progressIntervalBytes) {
                emit(
                    DownloadProgress(
                        descriptor.id,
                        DownloadPhase.Downloading,
                        total,
                        descriptor.sizeBytes,
                        url,
                        attempt
                    )
                )
                lastEmitted = total
            }
        }
    }

    private fun verifyLength(url: String) {
        val length = length()
        if (length > descriptor.sizeBytes) {
            PartialFiles.cleanup(partFile, metaFile)
            throw IOException("mirror '$url' sent more bytes than expected ($length > ${descriptor.sizeBytes})")
        }
        if (length < descriptor.sizeBytes) {
            throw IOException("mirror '$url' closed after $length of ${descriptor.sizeBytes} bytes")
        }
    }

    private fun requireContentRangeStart(connection: HttpURLConnection, offset: Long, url: String) {
        val header = connection.getHeaderField(HttpConnectionFactory.HEADER_CONTENT_RANGE) ?: return
        val start = CONTENT_RANGE.find(header)?.groupValues?.get(1)?.toLongOrNull() ?: return
        if (start != offset) {
            throw IOException("mirror '$url' returned an unexpected Content-Range start '$start' (wanted $offset)")
        }
    }

    private fun length(): Long = if (partFile.isFile) partFile.length() else 0L

    private fun describe(error: Exception): String = error.message ?: error::class.simpleName ?: error::class.java.name

    private companion object {
        val CONTENT_RANGE = Regex("^bytes\\s+(\\d+)-\\d+/\\d+$")
    }
}
