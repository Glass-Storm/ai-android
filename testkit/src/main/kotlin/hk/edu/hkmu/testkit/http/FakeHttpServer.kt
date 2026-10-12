package hk.edu.hkmu.testkit.http

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A dependency-free HTTP/1.1 server for tests.
 *
 * Binds to an ephemeral loopback port (`0` by default) and serves one handler per path; unmatched
 * paths return `404`. Connect a client to [urlFor] / [baseUrl]. Every request is recorded in
 * [requests] so tests can assert on method, path, headers (e.g. `Range`) and body. [respondWithBytes]
 * transparently answers `Range` requests with `206 Partial Content`, which the downloader end-to-end
 * test needs for resume.
 *
 * Deliberately uses only `java.net`/`java.io` (no OkHttp/MockWebServer) so it compiles into an
 * Android library and runs on both the JVM unit-test and on-device test classpaths.
 *
 * @param port Port to bind; `0` selects a free ephemeral port.
 */
class FakeHttpServer(port: Int = 0) : AutoCloseable {

    private val serverSocket = ServerSocket(port)
    private val running = AtomicBoolean(true)
    private val handlers = ConcurrentHashMap<String, (FakeHttpRequest) -> FakeHttpResponse>()
    private val recorded = CopyOnWriteArrayList<FakeHttpRequest>()

    init {
        thread(name = "fake-http-server", isDaemon = true) { acceptLoop() }
    }

    /** The actual bound port. */
    val port: Int get() = serverSocket.localPort

    /** Base URL of the form `http://127.0.0.1:<port>`. */
    val baseUrl: String get() = "http://127.0.0.1:$port"

    /** Every handled request, in arrival order. */
    val requests: List<FakeHttpRequest> get() = recorded.toList()

    /** Number of handled requests. */
    val requestCount: Int get() = recorded.size

    /** Registers [handler] for [path]. */
    fun on(path: String, handler: (FakeHttpRequest) -> FakeHttpResponse): FakeHttpServer {
        handlers[path] = handler
        return this
    }

    /** Serves a fixed [response] for [path]. */
    fun respondWith(path: String, response: FakeHttpResponse): FakeHttpServer = on(path) { response }

    /** Serves [body] for [path], optionally honouring byte `Range` requests. */
    fun respondWithBytes(
        path: String,
        body: ByteArray,
        enableRange: Boolean = true,
        contentType: String = "application/octet-stream"
    ): FakeHttpServer = on(path) { request -> rangedResponse(request, body, enableRange, contentType) }

    /** Absolute URL for [path] on this server. */
    fun urlFor(path: String): String = baseUrl + path

    override fun close() {
        running.set(false)
        runCatching { serverSocket.close() }
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = acceptOrNull() ?: return
            dispatch(socket)
        }
    }

    @Suppress("SwallowedException")
    private fun acceptOrNull(): Socket? = try {
        serverSocket.accept()
    } catch (e: IOException) {
        null
    }

    private fun dispatch(socket: Socket) {
        socket.use { connection ->
            val request = parseRequest(connection.getInputStream()) ?: return
            recorded += request
            val response = handlers[request.path]?.invoke(request) ?: FakeHttpResponse.notFound()
            writeResponse(connection.getOutputStream(), response)
        }
    }
}
