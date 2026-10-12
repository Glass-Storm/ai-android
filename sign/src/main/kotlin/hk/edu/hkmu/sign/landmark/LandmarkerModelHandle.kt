package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.registry.ModelHandle

/**
 * [ModelHandle] owning one native landmarker + its assembler.
 *
 * The shared [hk.edu.hkmu.core.registry.ModelRegistry] owns this handle for the whole residency of
 * the model and calls [close] exactly once; the registry's license gate has already run before
 * [hk.edu.hkmu.core.registry.ModelLoader.load] creates it.
 */
internal class LandmarkerModelHandle(
    private val backend: LandmarkerBackend,
    private val assembler: LandmarkResultAssembler
) : ModelHandle {

    /** Runs the landmarker and assembles the classifier-order [LandmarkFrame]. */
    fun detect(frame: VideoFrame): LandmarkFrame =
        assembler.assemble(backend.detect(frame.image, frame.timestampMillis))

    /** Frees the native landmarker. Called exactly once per residency. */
    override fun close() {
        backend.close()
    }
}
