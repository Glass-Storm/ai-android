@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign

import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File

/** Shared Tier A fixtures: dummy model files, deterministic logits and synthetic landmark windows. */
internal object SignTestModels {

    /** Materializes a dummy artifact for [spec] under [root] so asset validation passes. */
    fun prepare(root: File, spec: SignModelSpec) {
        val directory = File(root, spec.directoryName)
        directory.mkdirs()
        File(directory, spec.modelFile).writeBytes(byteArrayOf(0))
    }

    /** Logits with a single dominant class [peak] (confidence `e^10 / (e^10 + n - 1)`). */
    fun peakLogits(classes: Int, peak: Int): FloatArray = FloatArray(classes) { if (it == peak) PEAK else 0f }

    /** [count] frames of [valuesPerFrame] values each, with 1 ms-spaced presentation timestamps. */
    fun frames(count: Int, valuesPerFrame: Int, startMillis: Long = 0L): List<SignFrame> = List(count) { index ->
        SignFrame(
            landmarks = FloatArray(valuesPerFrame) { value -> (index + value).toFloat() },
            timestampMillis = startMillis + index
        )
    }

    private const val PEAK = 10f
}
