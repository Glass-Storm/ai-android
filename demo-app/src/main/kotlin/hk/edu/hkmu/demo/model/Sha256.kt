package hk.edu.hkmu.demo.model

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Streaming SHA-256 helper (JDK built-ins only). */
internal object Sha256 {
    private const val ALGORITHM = "SHA-256"
    private const val BUFFER_BYTES = 64 * 1024
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xff
    private const val HEX_BITS = 4
    private const val HEX_NIBBLE = 0x0f
    private const val HEX_DIGITS_PER_BYTE = 2

    /** Lowercase hex sha256 of [file]. */
    fun ofFile(file: File): String = file.inputStream().use(::digest)

    /** Lowercase hex sha256 of every byte read from [input]; does not close it. */
    fun digest(input: InputStream): String {
        val md = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            md.update(buffer, 0, read)
        }
        return toHex(md.digest())
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * HEX_DIGITS_PER_BYTE) {
        for (byte in bytes) {
            val value = byte.toInt() and BYTE_MASK
            append(Character.forDigit(value shr HEX_BITS, HEX_RADIX))
            append(Character.forDigit(value and HEX_NIBBLE, HEX_RADIX))
        }
    }
}
