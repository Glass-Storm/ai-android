@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.media

import java.io.DataInputStream
import java.io.File
import java.io.InputStream

private const val FOURCC = 4
private const val RIFF = "RIFF"
private const val WAVE = "WAVE"
private const val CHUNK_FMT = "fmt "
private const val CHUNK_DATA = "data"
private const val PCM_FORMAT_TAG = 1
private const val BITS_PER_SAMPLE = 16
private const val BYTES_PER_SAMPLE = 2
private const val MONO = 1
private const val FORMAT_CHUNK_BYTES = 16

/**
 * Minimal RIFF/WAVE reader for the bundled 16 kHz mono 16-bit PCM clip.
 *
 * Only PCM (`audioFormat == 1`) is supported. It walks the chunk list, keeps the `fmt ` description
 * and converts the `data` chunk's little-endian 16-bit samples to normalized Float32 in `[-1, 1]`
 * (the `AudioChunk` contract), so the real ASR engine receives exactly the pressure it expects.
 */
object WavReader {

    /** Parsed mono audio: normalized samples plus the file's sample rate. */
    class MonoAudio(val samples: FloatArray, val sampleRateHz: Int)

    /** Reads [file] as mono 16-bit PCM. */
    fun readMono(file: File): MonoAudio = file.inputStream().buffered().use { readMono(it) }

    private fun readMono(input: InputStream): MonoAudio {
        val stream = DataInputStream(input)
        require(stream.readAscii(FOURCC) == RIFF) { "not a RIFF file" }
        stream.readLeInt()
        require(stream.readAscii(FOURCC) == WAVE) { "not a WAVE file" }
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var formatTag = 0
        while (true) {
            val id = stream.readAscii(FOURCC)
            val size = stream.readLeInt()
            when (id) {
                CHUNK_FMT -> {
                    formatTag = stream.readLeShort()
                    channels = stream.readLeShort()
                    sampleRate = stream.readLeInt()
                    stream.readLeInt()
                    stream.readLeShort()
                    bits = stream.readLeShort()
                    skip(stream, size - FORMAT_CHUNK_BYTES)
                }
                CHUNK_DATA -> {
                    require(formatTag == PCM_FORMAT_TAG && bits == BITS_PER_SAMPLE && channels == MONO) {
                        "expected 16-bit mono PCM, got tag=$formatTag bits=$bits channels=$channels"
                    }
                    return MonoAudio(readSamples(stream, size), sampleRate)
                }
                else -> skip(stream, size)
            }
        }
    }

    private fun DataInputStream.readAscii(count: Int): String =
        String(ByteArray(count) { readByte() }, Charsets.US_ASCII)

    private fun DataInputStream.readLeShort(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun DataInputStream.readLeInt(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readSamples(stream: DataInputStream, byteCount: Int): FloatArray {
        val count = byteCount / BYTES_PER_SAMPLE
        val samples = FloatArray(count)
        for (index in 0 until count) {
            samples[index] = stream.readLeShort().toShort().toFloat() / Short.MAX_VALUE
        }
        return samples
    }

    private fun skip(stream: DataInputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                stream.readByte()
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
