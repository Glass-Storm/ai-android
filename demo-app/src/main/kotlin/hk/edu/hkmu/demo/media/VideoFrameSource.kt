@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import hk.edu.hkmu.sign.landmark.VideoFrame
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decodes a bundled video into [VideoFrame]s.
 *
 * The library owns no decoder and never opens a camera, so the demo supplies the frames: it points
 * [MediaMetadataRetriever] at the provisioned file and asks for a frame every [FRAME_INTERVAL_MS]
 * across the clip. The frames are handed to the sign pipeline, whose extractor consumes each bitmap
 * during `recognize`; the bitmaps are then left for GC.
 */
object VideoFrameSource {
    private const val FRAME_INTERVAL_MS = 33L
    private const val MICROS_PER_MILLI = 1_000L

    /** Streams the decoded frames of [file] in presentation order. */
    fun stream(file: File): Flow<VideoFrame> = flow {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = durationMs(retriever)
            var atMs = 0L
            while (atMs <= durationMs) {
                val bitmap = frameAt(retriever, atMs) ?: break
                emit(VideoFrame(bitmap, atMs))
                atMs += FRAME_INTERVAL_MS
            }
        } finally {
            retriever.release()
        }
    }

    private fun durationMs(retriever: MediaMetadataRetriever): Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    private fun frameAt(retriever: MediaMetadataRetriever, atMs: Long): Bitmap? =
        retriever.getFrameAtTime(atMs * MICROS_PER_MILLI, MediaMetadataRetriever.OPTION_CLOSEST)
}
