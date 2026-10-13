package hk.edu.hkmu.testkit.sign

import hk.edu.hkmu.sign.landmark.LandmarkExtractor
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import hk.edu.hkmu.sign.landmark.VideoFrame

/**
 * A no-op [LandmarkExtractor] for host-JVM (headless) tests that drive the extraction-free
 * `SignPipeline.recognizeLandmarks` seam.
 *
 * `SignPipeline.create` requires an extractor even when only the landmark path is used, and the
 * production extractor is device-only MediaPipe. The default [frameFor] throws, which makes this a
 * *proof* that the landmark path bypasses extraction; a test that wants the [VideoFrame] path supplies
 * its own mapping.
 *
 * @param frameFor Maps a decoded [VideoFrame] to a [LandmarkFrame]; defaults to "extraction not
 *   expected".
 */
class FakeLandmarkExtractor(
    private val frameFor: (VideoFrame) -> LandmarkFrame = { error("extraction not expected on the landmark path") }
) : LandmarkExtractor {

    /** True once [close] was called. */
    var closed: Boolean = false
        private set

    override fun extract(frame: VideoFrame): LandmarkFrame = frameFor(frame)

    override fun close() {
        closed = true
    }
}
