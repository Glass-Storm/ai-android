package hk.edu.hkmu.testkit.http

import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Header names used by the fake HTTP codec. */
internal const val HEADER_CONTENT_LENGTH = "Content-Length"

/** Range request header. */
internal const val HEADER_RANGE = "Range"

/** Range response header. */
internal const val HEADER_CONTENT_RANGE = "Content-Range"

/** Connection header (always `close` for the fake server). */
internal const val HEADER_CONNECTION = "Connection"

/**
 * Parses one HTTP/1.1 request from [input]. Returns null when the stream is already at EOF (the
 * client closed the connection without sending anything).
 */
internal fun parseRequest(input: java.io.InputStream): FakeHttpRequest? {
    val reader = input.bufferedReader(StandardCharsets.US_ASCII)
    val requestLine = reader.readLine() ?: return null
    val parts = requestLine.split(' ')
    val headers = readHeaders(reader)
    val body = readBody(reader, headers)
    return FakeHttpRequest(parts.first(), parts.getOrElse(1) { "" }, headers, body)
}

private fun readHeaders(reader: BufferedReader): Map<String, String> {
    val headers = LinkedHashMap<String, String>()
    var line = reader.readLine()
    while (line != null && line.isNotEmpty()) {
        val separator = line.indexOf(':')
        if (separator > 0) {
            headers[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
        }
        line = reader.readLine()
    }
    return headers
}

private fun readBody(reader: BufferedReader, headers: Map<String, String>): ByteArray {
    val length = headers.entries
        .firstOrNull { it.key.equals(HEADER_CONTENT_LENGTH, ignoreCase = true) }
        ?.value?.toIntOrNull() ?: 0
    if (length <= 0) return ByteArray(0)
    val chars = CharArray(length)
    var read = 0
    while (read < length) {
        val next = reader.read(chars, read, length - read)
        if (next < 0) break
        read += next
    }
    return String(chars, 0, read).toByteArray(StandardCharsets.UTF_8)
}

/** Writes [response] to [output] as an HTTP/1.1 message and flushes. */
internal fun writeResponse(output: OutputStream, response: FakeHttpResponse) {
    val builder = StringBuilder()
    builder.append("HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n")
    builder.append("$HEADER_CONTENT_LENGTH: ${response.body.size}\r\n")
    builder.append("$HEADER_CONNECTION: close\r\n")
    response.headers.forEach { (name, value) -> builder.append("$name: $value\r\n") }
    builder.append("\r\n")
    output.write(builder.toString().toByteArray(StandardCharsets.US_ASCII))
    output.write(response.body)
    output.flush()
}

private fun reasonPhrase(status: Int): String = when (status) {
    FakeHttpResponse.STATUS_OK -> "OK"
    FakeHttpResponse.STATUS_PARTIAL_CONTENT -> "Partial Content"
    FakeHttpResponse.STATUS_NOT_FOUND -> "Not Found"
    FakeHttpResponse.STATUS_RANGE_NOT_SATISFIABLE -> "Range Not Satisfiable"
    FakeHttpResponse.STATUS_INTERNAL_ERROR -> "Internal Server Error"
    else -> "OK"
}

/**
 * Builds a response for [body], honouring a `Range` request header when [enableRange] is true.
 *
 * A satisfiable range yields `206 Partial Content` with a `Content-Range` header; an unsatisfiable
 * one yields `416`; a request without a `Range` header yields the whole body as `200`.
 */
internal fun rangedResponse(
    request: FakeHttpRequest,
    body: ByteArray,
    enableRange: Boolean,
    contentType: String
): FakeHttpResponse {
    val baseHeaders = mapOf("Content-Type" to contentType)
    val rangeHeader = request.headerValue(HEADER_RANGE)
    if (!enableRange || rangeHeader == null) {
        return FakeHttpResponse(FakeHttpResponse.STATUS_OK, baseHeaders, body)
    }
    val range = parseByteRange(rangeHeader, body.size)
    val response = if (range == null) {
        FakeHttpResponse(
            FakeHttpResponse.STATUS_RANGE_NOT_SATISFIABLE,
            baseHeaders + (HEADER_CONTENT_RANGE to "bytes */${body.size}"),
            ByteArray(0)
        )
    } else {
        FakeHttpResponse(
            FakeHttpResponse.STATUS_PARTIAL_CONTENT,
            baseHeaders + (HEADER_CONTENT_RANGE to "bytes ${range.first}-${range.last}/${body.size}"),
            body.copyOfRange(range.first, range.last + 1)
        )
    }
    return response
}

/** Parses a single `bytes=start-end` range against a body of [size]; null when unsatisfiable. */
internal fun parseByteRange(header: String, size: Int): IntRange? {
    val hasPrefix = header.startsWith("bytes=")
    val spec = if (hasPrefix) header.removePrefix("bytes=").substringBefore(',') else ""
    val dash = spec.indexOf('-')
    val range = if (!hasPrefix || dash < 0) {
        null
    } else {
        val start = spec.substring(0, dash).toIntOrNull() ?: 0
        val end = spec.substring(dash + 1).toIntOrNull() ?: (size - 1)
        if (start < 0 || start >= size || end < start) null else start..minOf(end, size - 1)
    }
    return range
}
