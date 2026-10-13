package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.engine.DiarizationSegment

/**
 * Tier A fake for the [DiarizationBackend] seam.
 *
 * Returns canned [segments] and records every call and `close`, so the module's Flow wiring, ordering
 * and lifecycle can be asserted without any native library or model.
 */
internal class FakeDiarizationBackend(
    private val segments: List<DiarizationSegment> = emptyList()
) : DiarizationBackend {

    /** Sample count of each received call, in call order. */
    val diarizeCallSampleCounts: MutableList<Int> = mutableListOf()

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    override fun diarize(samples: FloatArray): List<DiarizationSegment> {
        diarizeCallSampleCounts += samples.size
        return segments
    }

    override fun close() {
        closed = true
    }
}
