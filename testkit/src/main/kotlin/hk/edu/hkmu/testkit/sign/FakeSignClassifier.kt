package hk.edu.hkmu.testkit.sign

import hk.edu.hkmu.sign.classifier.SignClassifier
import kotlin.math.ln

/**
 * Deterministic, native-free [SignClassifier] for host-JVM (headless) tests.
 *
 * A headless CI cannot load the LiteRT runtime, so this returns a caller-supplied logit vector (or a
 * peak vector derived from an argmax index and a target confidence). It records every input it saw
 * and tracks [closed], so a pipeline's lazy-acquire/close lifecycle can be asserted with no native
 * library and no model file.
 *
 * @property logits Raw `[classCount]` logits returned by every [classify] call.
 */
class FakeSignClassifier(private val logits: FloatArray) : SignClassifier {

    /**
     * Builds a classifier whose softmax peaks at [argMaxIndex] with approximately [confidence].
     *
     * @param classCount Number of output classes; must be at least 2.
     * @param argMaxIndex Index of the winning class; must be within `0 until classCount`.
     * @param confidence Target softmax confidence in `(0, 1)`.
     */
    constructor(classCount: Int, argMaxIndex: Int, confidence: Float) : this(
        peakLogits(classCount, argMaxIndex, confidence)
    )

    /** Inputs passed to [classify], copied on capture, in call order. */
    val inputs: MutableList<FloatArray> = mutableListOf()

    /** True once [close] was called. */
    var closed: Boolean = false
        private set

    override fun classify(input: FloatArray): FloatArray {
        inputs += input.copyOf()
        return logits.copyOf()
    }

    override fun close() {
        closed = true
    }

    companion object {
        /**
         * Logits with a single dominant class at [peak], scaled so that `softmax(logits)[peak]`
         * approximates [confidence] across [classes] outputs.
         *
         * @param classes Number of output classes; must be at least 2.
         * @param peak Index of the dominant class; must be within `0 until classes`.
         * @param confidence Target softmax confidence in `(0, 1)`.
         */
        fun peakLogits(classes: Int, peak: Int, confidence: Float): FloatArray {
            require(classes >= 2) { "classes must be at least 2" }
            require(peak in 0 until classes) { "peak must be within 0 until classes" }
            require(confidence > 0f && confidence < 1f) { "confidence must be within (0, 1)" }
            val peakLogit = ln(confidence.toDouble() * (classes - 1) / (1.0 - confidence)).toFloat()
            return FloatArray(classes) { index -> if (index == peak) peakLogit else 0f }
        }
    }
}
