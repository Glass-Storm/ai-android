package hk.edu.hkmu.tts.pcm

import hk.edu.hkmu.core.engine.AudioChunk
import kotlin.math.roundToInt

/**
 * A chunk of 16-bit signed mono PCM at the model-reported sample rate.
 *
 * @property samples 16-bit signed samples.
 * @property sampleRateHz Sample rate of [samples] (from the loaded model).
 * @property speakerId Speaker id used for synthesis.
 */
class Int16Chunk(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val speakerId: Int
)

/**
 * Converts normalized Float32 PCM in `[-1.0, 1.0]` to 16-bit signed PCM.
 *
 * Out-of-range samples are clipped; the scale is `Short.MAX_VALUE` (32767). This is the boundary
 * conversion for encoders/players that only accept 16-bit audio — the public [AudioChunk] contract
 * stays float to avoid a lossy intermediate representation.
 */
fun toInt16(samples: FloatArray): ShortArray {
    val output = ShortArray(samples.size)
    for (index in samples.indices) {
        val clamped = samples[index].coerceIn(FLOAT_MIN, FLOAT_MAX)
        val scaled = (clamped * Short.MAX_VALUE).roundToInt()
        output[index] = scaled.coerceIn(SHORT_MIN, SHORT_MAX).toShort()
    }
    return output
}

/** Converts this normalized Float32 chunk to a 16-bit PCM chunk tagged with [speakerId]. */
fun AudioChunk.toInt16(speakerId: Int): Int16Chunk = Int16Chunk(toInt16(samples), sampleRateHz, speakerId)

private const val FLOAT_MIN = -1.0f
private const val FLOAT_MAX = 1.0f
private const val SHORT_MIN = -32_768
private const val SHORT_MAX = 32_767
