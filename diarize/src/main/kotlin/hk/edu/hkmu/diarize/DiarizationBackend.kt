package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.engine.DiarizationSegment

/**
 * Narrow internal seam over a diarization backend.
 *
 * [SherpaDiarizationEngine] depends only on this interface, never on the sherpa-onnx JNI types
 * directly. Tier A (host JVM) tests substitute a fake so the module's Flow wiring, ordering and
 * license gate can be exercised without loading any native library or model.
 */
internal interface DiarizationBackend : AutoCloseable {

    /**
     * Diarizes one full utterance of 16 kHz mono PCM (normalized Float32 in `[-1, 1]`).
     *
     * This is an offline, whole-utterance call: it returns every speaker turn at once, in
     * chronological order.
     */
    fun diarize(samples: FloatArray): List<DiarizationSegment>

    /** Releases the underlying native session. Idempotent. */
    override fun close() {}
}

/** Constructs the production [DiarizationBackend] from resolved assets. */
internal fun interface DiarizationBackendFactory {
    /** Creates the backend; called at load, never at engine construction. */
    fun create(): DiarizationBackend
}
