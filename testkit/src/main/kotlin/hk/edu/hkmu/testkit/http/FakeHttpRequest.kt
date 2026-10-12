package hk.edu.hkmu.testkit.http

import java.nio.charset.StandardCharsets

/**
 * A request captured by [FakeHttpServer], retained so tests can assert on method, path, headers and
 * body.
 *
 * @property method HTTP method (`GET`, `HEAD`, ...).
 * @property path Request path without the query string.
 * @property headers Header name/value pairs as received (original casing preserved).
 * @property body Raw request body bytes (empty when none was sent).
 */
class FakeHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    /** Case-insensitive header lookup. */
    fun headerValue(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** The body decoded as UTF-8 text. */
    val bodyText: String get() = String(body, StandardCharsets.UTF_8)

    override fun toString(): String = "$method $path"
}
