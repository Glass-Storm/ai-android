@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay

/**
 * Plays a mono normalized Float32 buffer through an [AudioTrack] in `ENCODING_PCM_FLOAT`.
 *
 * Waits (via [delay]) for the clip's duration before releasing the track, so the demo does not cut
 * playback short. The static mode is fine for the short synthesized utterance.
 */
object AudioPlayer {
    private const val MILLIS_PER_SECOND = 1_000L
    private const val TAIL_MILLIS = 250L
    private const val BYTES_PER_FLOAT = 4
    private const val MIN_BUFFER_BYTES = 4096

    /** Plays [samples] at [sampleRateHz] and returns once playback has finished. */
    suspend fun play(samples: FloatArray, sampleRateHz: Int) {
        if (samples.isEmpty() || sampleRateHz <= 0) {
            return
        }
        val track = buildTrack(samples.size, sampleRateHz)
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            delay(samples.size * MILLIS_PER_SECOND / sampleRateHz + TAIL_MILLIS)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun buildTrack(sampleCount: Int, sampleRateHz: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes((sampleCount * BYTES_PER_FLOAT).coerceAtLeast(MIN_BUFFER_BYTES))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
}
