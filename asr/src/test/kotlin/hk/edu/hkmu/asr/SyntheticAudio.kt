package hk.edu.hkmu.asr

import hk.edu.hkmu.core.engine.AudioChunk
import kotlin.math.PI
import kotlin.math.sin

/** Deterministic PCM generators for Tier A (host JVM) tests of the segmentation + Flow wiring. */
internal object SyntheticAudio {

    const val SAMPLE_RATE_HZ = 16_000
    const val DEFAULT_AMPLITUDE = 0.5f
    const val DEFAULT_FREQUENCY_HZ = 440.0

    fun silence(durationMillis: Int, sampleRateHz: Int = SAMPLE_RATE_HZ): FloatArray =
        FloatArray(sampleCount(durationMillis, sampleRateHz))

    fun tone(
        durationMillis: Int,
        sampleRateHz: Int = SAMPLE_RATE_HZ,
        amplitude: Float = DEFAULT_AMPLITUDE,
        frequencyHz: Double = DEFAULT_FREQUENCY_HZ
    ): FloatArray {
        val count = sampleCount(durationMillis, sampleRateHz)
        return FloatArray(count) { index ->
            (amplitude * sin(2.0 * PI * frequencyHz * index / sampleRateHz)).toFloat()
        }
    }

    fun chunks(samples: FloatArray, chunkSize: Int, sampleRateHz: Int = SAMPLE_RATE_HZ): List<AudioChunk> =
        samples.asList().chunked(chunkSize).map { AudioChunk(it.toFloatArray(), sampleRateHz) }

    private fun sampleCount(durationMillis: Int, sampleRateHz: Int): Int =
        durationMillis * sampleRateHz / MILLIS_PER_SECOND

    private const val MILLIS_PER_SECOND = 1_000
}
