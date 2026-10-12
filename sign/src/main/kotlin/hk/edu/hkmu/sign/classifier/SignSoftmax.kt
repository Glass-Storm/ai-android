package hk.edu.hkmu.sign.classifier

import kotlin.math.exp

/**
 * Softmax + argmax over raw classifier logits.
 *
 * Pure Kotlin so confidence computation is host-testable. The max-subtraction keeps `exp` in range for
 * the large logits the TGCN emits.
 */
internal object SignSoftmax {

    /** Returns the softmax distribution of [logits]; an empty input yields an empty result. */
    fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) {
            return FloatArray(0)
        }
        var max = logits[0]
        for (value in logits) {
            if (value > max) {
                max = value
            }
        }
        val out = FloatArray(logits.size)
        var sum = 0.0
        for (index in logits.indices) {
            val weight = exp((logits[index] - max).toDouble())
            out[index] = weight.toFloat()
            sum += weight
        }
        for (index in out.indices) {
            out[index] = (out[index] / sum).toFloat()
        }
        return out
    }

    /** Index of the largest value in [values] (first wins on ties); 0 for an empty input. */
    fun argMax(values: FloatArray): Int {
        var best = 0
        for (index in values.indices) {
            if (values[index] > values[best]) {
                best = index
            }
        }
        return best
    }
}
