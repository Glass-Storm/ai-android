package hk.edu.hkmu.models.store

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A minimal in-test HTTP/1.1 server built on `java.net.ServerSocket`.
 *
 * This is deliberately NOT the shared `testkit` fake server: todo 11 owns its own server so it does
 * not depend on todo 12. It also stands in for the JDK `com.sun.net.httpserver` server: the Android
 * unit-test Kotlin compile runs against `android.jar`, which does not ship `com.sun.net.httpserver`,
 * so a `ServerSocket` server is used instead (both are JDK/Android stdlib — still zero new deps).
 *
 * Each test registers path-scoped [route]s; every request (path + `Range` start) is recorded so
 * offline/metered/license/dedup tests can assert that no request was made.
 */
internal class FakeHttpServer : AutoCloseable {
    data class RecordedRequest(val path: String, val rangeStart: Long?)

    /** A canned HTTP response. [body] writes the actual bytes (which may be fewer than [contentLength]). */
    class Response(
        val status: Int,
        val contentLength: Long,
        val body: (OutputStream) -> Unit = {},
        val headers: Map<String, String> = emptyMap(),
        val delayMillis: Long = 0L
    )

    val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> Response>()
    private val connections = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket(ANY_PORT, BACKLOG, InetAddress.getByName(LOOPBACK))
    private val acceptThread = Thread(::acceptLoop, "fake-http-accept").apply { isDaemon = true }

    val baseUrl: String get() = "http://$LOOPBACK:${serverSocket.localPort}"

    fun url(path: String): String = baseUrl + path

    fun route(path: String, handler: (RecordedRequest) -> Response): FakeHttpServer {
        routes[path] = handler
        return this
    }

    fun start(): FakeHttpServer {
        acceptThread.start()
        return this
    }

    fun requestCount(path: String? = null): Int =
        if (path == null) requests.size else requests.count { it.path == path }

    override fun close() {
        runCatching { serverSocket.close() }
        connections.shutdownNow()
        acceptThread.interrupt()
    }

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val socket = try {
                serverSocket.accept()
            } catch (_: Exception) {
                return
            }
            connections.execute { runCatching { serve(socket) } }
        }
    }

    private fun serve(socket: Socket) {
        socket.use { connection ->
            val input = BufferedInputStream(connection.getInputStream())
            val request = readRequest(input) ?: return
            requests += request
            val response = routes[request.path]?.invoke(request) ?: NOT_FOUND
            if (response.delayMillis > 0L) Thread.sleep(response.delayMillis)
            val output = BufferedOutputStream(connection.getOutputStream())
            output.write(head(response))
            output.flush()
            response.body(output)
            output.flush()
        }
    }

    private fun readRequest(input: BufferedInputStream): RecordedRequest? {
        val requestLine = readLine(input) ?: return null
        val path = requestLine.split(' ').getOrNull(1)
        var rangeStart: Long? = null
        var line = readLine(input)
        while (line != null && line.isNotEmpty()) {
            if (line.startsWith(RANGE_HEADER, ignoreCase = true)) {
                rangeStart = parseRangeStart(line.substringAfter(':'))
            }
            line = readLine(input)
        }
        return path?.let { RecordedRequest(it, rangeStart) }
    }

    private fun readLine(input: BufferedInputStream): String? {
        var byte = input.read()
        if (byte < 0) return null
        val buffer = ByteArrayOutputStream()
        while (byte >= 0 && byte != NEWLINE) {
            if (byte != CARRIAGE_RETURN) buffer.write(byte)
            byte = input.read()
        }
        return buffer.toString(ISO_8859_1)
    }

    private fun head(response: Response): ByteArray {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append(CRLF)
        builder.append("Connection: close").append(CRLF)
        builder.append("Content-Length: ").append(response.contentLength).append(CRLF)
        response.headers.forEach { (name, value) -> builder.append(name).append(": ").append(value).append(CRLF) }
        builder.append(CRLF)
        return builder.toString().toByteArray(ISO_8859_1)
    }

    private fun reason(status: Int): String = when (status) {
        STATUS_OK -> "OK"
        STATUS_PARTIAL -> "Partial Content"
        STATUS_NOT_FOUND -> "Not Found"
        STATUS_RANGE_NOT_SATISFIABLE -> "Range Not Satisfiable"
        else -> "Server Error"
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val ANY_PORT = 0
        private const val BACKLOG = 64
        private const val NEWLINE = '\n'.code
        private const val CARRIAGE_RETURN = '\r'.code
        private const val CRLF = "\r\n"
        private const val RANGE_HEADER = "Range:"
        const val STATUS_OK = 200
        const val STATUS_PARTIAL = 206
        const val STATUS_NOT_FOUND = 404
        const val STATUS_RANGE_NOT_SATISFIABLE = 416

        private val NOT_FOUND = Response(STATUS_NOT_FOUND, 0L)
        private val ISO_8859_1 = Charsets.ISO_8859_1
        private val RANGE_PATTERN = Regex("bytes=(\\d+)-")

        fun parseRangeStart(header: String?): Long? =
            header?.let { RANGE_PATTERN.find(it)?.groupValues?.get(1)?.toLongOrNull() }
    }
}

/** Ready-made [FakeHttpServer] route handlers for the downloader suite. */
internal object Respond {
    private const val STATUS_OK = FakeHttpServer.STATUS_OK
    private const val STATUS_PARTIAL = FakeHttpServer.STATUS_PARTIAL

    /**
     * Serves [body], honouring `Range` with a `206` when [supportsRange] is true.
     *
     * @param delayMillis slept before response headers (drives the timeout scenario).
     * @param chunkBytes when `> 0`, the body is streamed in chunks of this size.
     * @param chunkDelayMillis slept between chunks (drives the cancellation scenario).
     */
    fun bytes(
        body: ByteArray,
        supportsRange: Boolean = true,
        delayMillis: Long = 0L,
        chunkBytes: Int = 0,
        chunkDelayMillis: Long = 0L
    ): (FakeHttpServer.RecordedRequest) -> FakeHttpServer.Response = { request ->
        val start = request.rangeStart
        if (supportsRange && start != null && start in 0..body.size.toLong()) {
            val from = start.toInt()
            FakeHttpServer.Response(
                status = STATUS_PARTIAL,
                contentLength = (body.size - from).toLong(),
                headers = mapOf("Content-Range" to "bytes $from-${body.size - 1}/${body.size}"),
                body = { output -> writeChunks(output, body, from, chunkBytes, chunkDelayMillis) },
                delayMillis = delayMillis
            )
        } else {
            FakeHttpServer.Response(
                status = STATUS_OK,
                contentLength = body.size.toLong(),
                body = { output -> writeChunks(output, body, 0, chunkBytes, chunkDelayMillis) },
                delayMillis = delayMillis
            )
        }
    }

    /** Serves a fixed [status] with an empty body. */
    fun status(status: Int): (FakeHttpServer.RecordedRequest) -> FakeHttpServer.Response =
        { FakeHttpServer.Response(status, 0L) }

    /** Declares the full [body] length but writes only the first [limit] bytes, then closes. */
    fun truncate(body: ByteArray, limit: Int): (FakeHttpServer.RecordedRequest) -> FakeHttpServer.Response = {
        FakeHttpServer.Response(
            status = STATUS_OK,
            contentLength = body.size.toLong(),
            body = { output ->
                output.write(body, 0, limit)
                output.flush()
            }
        )
    }

    private fun writeChunks(output: OutputStream, body: ByteArray, from: Int, chunkBytes: Int, delayMillis: Long) {
        val size = if (chunkBytes > 0) chunkBytes else body.size - from
        var offset = from
        while (offset < body.size) {
            val count = minOf(size, body.size - offset)
            output.write(body, offset, count)
            output.flush()
            offset += count
            if (delayMillis > 0L && offset < body.size) Thread.sleep(delayMillis)
        }
    }
}
