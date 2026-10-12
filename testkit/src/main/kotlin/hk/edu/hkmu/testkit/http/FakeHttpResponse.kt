package hk.edu.hkmu.testkit.http

import java.nio.charset.StandardCharsets

/**
 * A canned response returned by [FakeHttpServer].
 *
 * Use the [text] helper for text bodies; the constants cover the status codes a download/resume
 * test needs (200, 206, 404, 416, 500).
 *
 * @property status HTTP status code.
 * @property headers Extra response headers.
 * @property body Raw response body bytes.
 */
class FakeHttpResponse(
    val status: Int = STATUS_OK,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = EMPTY_BODY
) {
    /** The body decoded as UTF-8 text. */
    val bodyText: String get() = String(body, StandardCharsets.UTF_8)

    companion object {
        /** `200 OK`. */
        const val STATUS_OK: Int = 200

        /** `206 Partial Content`. */
        const val STATUS_PARTIAL_CONTENT: Int = 206

        /** `404 Not Found`. */
        const val STATUS_NOT_FOUND: Int = 404

        /** `416 Range Not Satisfiable`. */
        const val STATUS_RANGE_NOT_SATISFIABLE: Int = 416

        /** `500 Internal Server Error`. */
        const val STATUS_INTERNAL_ERROR: Int = 500

        private val EMPTY_BODY: ByteArray = ByteArray(0)

        /** Builds a response with a UTF-8 text body. */
        fun text(
            body: String,
            status: Int = STATUS_OK,
            contentType: String = "text/plain; charset=utf-8"
        ): FakeHttpResponse {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            return FakeHttpResponse(status, mapOf("Content-Type" to contentType), bytes)
        }

        /** Builds a `404 Not Found` response. */
        fun notFound(): FakeHttpResponse = text("not found", STATUS_NOT_FOUND)
    }
}
