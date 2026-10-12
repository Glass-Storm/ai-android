package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.config.DEFAULT_SAMPLE_RATE_HZ

/**
 * A chunk of mono PCM audio.
 *
 * Samples are normalized Float32 in `[-1.0, 1.0]`. Encoders that need 16-bit PCM convert at the
 * boundary; the public contract stays float to avoid a lossy intermediate representation.
 *
 * @property samples Raw sample data.
 * @property sampleRateHz Sample rate (Hz) of [samples].
 */
data class AudioChunk(
    val samples: FloatArray,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
)
