package hk.edu.hkmu.models.store

import java.net.HttpURLConnection
import java.net.URI

/**
 * Opens configured [HttpURLConnection]s for a download.
 *
 * Kept as its own type so the HTTP specifics (method, timeouts, range header, identity encoding)
 * live in one place. The library intentionally uses the JDK/Android built-in client — no new
 * runtime dependency — and tests exercise it against a real in-test `com.sun.net.httpserver`.
 */
internal class HttpConnectionFactory(private val config: ModelStoreConfig) {

    /**
     * Opens (but does not connect) a GET request for [url].
     *
     * @param rangeStart when `> 0`, a `Range: bytes=<rangeStart>-` header is sent to resume.
     */
    fun open(url: String, rangeStart: Long): HttpURLConnection {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = config.connectTimeoutMillis
        connection.readTimeout = config.readTimeoutMillis
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty(HEADER_ACCEPT_ENCODING, ENCODING_IDENTITY)
        if (rangeStart > 0L) {
            connection.setRequestProperty(HEADER_RANGE, "bytes=$rangeStart-")
        }
        return connection
    }

    companion object {
        const val HEADER_RANGE = "Range"
        const val HEADER_CONTENT_RANGE = "Content-Range"
        const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        const val ENCODING_IDENTITY = "identity"

        const val STATUS_OK = 200
        const val STATUS_PARTIAL = 206
        const val STATUS_RANGE_NOT_SATISFIABLE = 416
        const val MIN_SERVER_ERROR = 500
    }
}
