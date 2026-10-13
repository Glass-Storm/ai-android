@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.media

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Encodes one CameraX `ImageAnalysis` frame into a standalone JPEG still.
 *
 * `ImageAnalysis` emits YUV_420_888, but `YuvImage` only accepts NV21 or YUY2, so the three planes
 * are repacked into NV21 — luma first, then the two chroma planes interleaved as V/U — honouring
 * each plane's row and pixel stride. The bytes returned here are exactly what the caller feeds to
 * `BitmapFactory.decodeByteArray`, which is the per-image MediaPipe path the hub drives.
 */
object CameraFrameEncoder {

    private const val JPEG_QUALITY = 85
    private const val CHROMA = 2

    /** JPEG encoding of the frame held by [proxy]; valid only until [proxy] is closed. */
    fun toJpeg(proxy: ImageProxy): ByteArray {
        val nv21 = toNv21(proxy)
        val jpeg = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
            .compressToJpeg(Rect(0, 0, proxy.width, proxy.height), JPEG_QUALITY, jpeg)
        return jpeg.toByteArray()
    }

    private fun toNv21(proxy: ImageProxy): ByteArray {
        val width = proxy.width
        val height = proxy.height
        val luma = proxy.planes[0]
        val u = proxy.planes[1]
        val v = proxy.planes[2]
        val lumaSize = width * height
        val nv21 = ByteArray(lumaSize + lumaSize / CHROMA)

        var lumaPos = 0
        for (row in 0 until height) {
            val rowStart = row * luma.rowStride
            for (column in 0 until width) {
                nv21[lumaPos++] = luma.buffer.get(rowStart + column)
            }
        }

        var chromaPos = lumaSize
        for (row in 0 until height / CHROMA) {
            for (column in 0 until width / CHROMA) {
                nv21[chromaPos++] = v.buffer.get(row * v.rowStride + column * v.pixelStride)
                nv21[chromaPos++] = u.buffer.get(row * u.rowStride + column * u.pixelStride)
            }
        }
        return nv21
    }
}
