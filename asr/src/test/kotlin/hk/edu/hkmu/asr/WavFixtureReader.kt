package hk.edu.hkmu.asr

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for the committed 16 kHz mono 16-bit PCM WAV fixtures.
 *
 * Test-only: exists so the held-out set under `src/test/resources/fixtures/asr/` can be validated
 * on the host and fed through the engine's fake path before the device run.
 */
internal object WavFixtureReader {

    class WavData(
        val sampleRateHz: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val samples: FloatArray
    )

    fun read(resourcePath: String): WavData {
        val bytes = WavFixtureReader::class.java.getResourceAsStream(resourcePath)?.readBytes()
            ?: error("missing fixture resource: $resourcePath")
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): WavData {
        require(bytes.size >= MIN_HEADER_BYTES) { "wav too small" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(readTag(buffer) == RIFF) { "not a RIFF file" }
        buffer.int
        require(readTag(buffer) == WAVE) { "not a WAVE file" }

        var format = 0
        var channels = 0
        var sampleRateHz = 0
        var bitsPerSample = 0
        var samples = FloatArray(0)

        while (buffer.remaining() >= CHUNK_HEADER_BYTES) {
            val id = readTag(buffer)
            val size = buffer.int
            val dataStart = buffer.position()
            when (id) {
                FMT -> {
                    format = buffer.short.toInt()
                    channels = buffer.short.toInt()
                    sampleRateHz = buffer.int
                    buffer.int
                    buffer.short
                    bitsPerSample = buffer.short.toInt()
                }

                DATA -> samples = readSamples(buffer, size)
                else -> Unit
            }
            seekPastChunk(buffer, dataStart, size)
        }

        require(format == PCM_FORMAT) { "expected PCM WAV, got format $format" }
        return WavData(sampleRateHz, channels, bitsPerSample, samples)
    }

    private fun readSamples(buffer: ByteBuffer, byteCount: Int): FloatArray {
        val count = byteCount / BYTES_PER_SAMPLE
        return FloatArray(count) {
            (buffer.short.toFloat() / Short.MAX_VALUE).coerceIn(MIN_SAMPLE, MAX_SAMPLE)
        }
    }

    private fun seekPastChunk(buffer: ByteBuffer, dataStart: Int, size: Int) {
        val padded = size + (size and 1)
        buffer.position(minOf(dataStart + padded, buffer.capacity()))
    }

    private fun readTag(buffer: ByteBuffer): String {
        val tag = ByteArray(TAG_BYTES)
        buffer.get(tag)
        return String(tag, Charsets.US_ASCII)
    }

    private const val TAG_BYTES = 4
    private const val CHUNK_HEADER_BYTES = 8
    private const val MIN_HEADER_BYTES = 44
    private const val BYTES_PER_SAMPLE = 2
    private const val PCM_FORMAT = 1
    private const val MIN_SAMPLE = -1.0f
    private const val MAX_SAMPLE = 1.0f
    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FMT = "fmt "
    private const val DATA = "data"
}
