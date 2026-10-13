package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.engine.AudioChunk

/** Deterministic PCM generators for Tier A (host JVM) tests of the Flow wiring. */
internal object SyntheticAudio {

    const val SAMPLE_RATE_HZ = 16_000

    fun silence(durationMillis: Int, sampleRateHz: Int = SAMPLE_RATE_HZ): FloatArray =
        FloatArray(durationMillis * sampleRateHz / MILLIS_PER_SECOND)

    fun chunks(samples: FloatArray, chunkSize: Int, sampleRateHz: Int = SAMPLE_RATE_HZ): List<AudioChunk> =
        samples.asList().chunked(chunkSize).map { AudioChunk(it.toFloatArray(), sampleRateHz) }

    private const val MILLIS_PER_SECOND = 1_000
}
