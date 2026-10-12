@file:Suppress("MagicNumber")

package hk.edu.hkmu.testkit.http

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the fake HTTP server serves, ranges, 404s and records requests. */
class FakeHttpServerTest {

    @Test
    fun `serves a fixed text response`() {
        FakeHttpServer().use { server ->
            server.respondWith("/hello", FakeHttpResponse.text("hi there"))

            val result = get(server.urlFor("/hello"))

            assertEquals(200, result.status)
            assertEquals("hi there", result.bodyText)
        }
    }

    @Test
    fun `unknown path returns 404`() {
        FakeHttpServer().use { server ->
            val result = get(server.urlFor("/missing"))

            assertEquals(404, result.status)
        }
    }

    @Test
    fun `honours a byte range request`() {
        FakeHttpServer().use { server ->
            server.respondWithBytes("/model.bin", "0123456789".toByteArray(StandardCharsets.UTF_8))

            val result = get(server.urlFor("/model.bin"), mapOf("Range" to "bytes=2-4"))

            assertEquals(206, result.status)
            assertEquals("bytes 2-4/10", result.contentRange)
            assertEquals("234", result.bodyText)
        }
    }

    @Test
    fun `without a range header returns the whole body as 200`() {
        FakeHttpServer().use { server ->
            server.respondWithBytes("/model.bin", "abcdef".toByteArray(StandardCharsets.UTF_8))

            val result = get(server.urlFor("/model.bin"))

            assertEquals(200, result.status)
            assertEquals("abcdef", result.bodyText)
        }
    }

    @Test
    fun `records requests with headers`() {
        FakeHttpServer().use { server ->
            server.respondWith("/p", FakeHttpResponse.text("ok"))

            get(server.urlFor("/p"), mapOf("X-Test" to "yes"))

            assertEquals(1, server.requestCount)
            assertEquals("/p", server.requests.single().path)
            assertEquals("yes", server.requests.single().headerValue("x-test"))
        }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        val contentRange = connection.getHeaderField("Content-Range")
        connection.disconnect()
        return HttpResult(status, contentRange, body)
    }

    private class HttpResult(val status: Int, val contentRange: String?, val body: ByteArray) {
        val bodyText: String get() = String(body, StandardCharsets.UTF_8)
    }
}
