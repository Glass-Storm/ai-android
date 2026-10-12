package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.core.registry.ModelHandle
import hk.edu.hkmu.sign.model.SignModelSpec

/**
 * Owns one resident classifier for the registry.
 *
 * Created by the registry's loader after the license gate and asset validation pass, and closed by the
 * registry on unload/eviction. It carries the [spec] so the engine can read the input contract and
 * label table without a second lookup.
 */
internal class SignClassifierHandle(
    val spec: SignModelSpec,
    private val classifier: SignClassifier
) : ModelHandle {

    private var closed: Boolean = false

    /** Delegates to the underlying classifier. */
    fun classify(input: FloatArray): FloatArray = classifier.classify(input)

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        classifier.close()
    }
}
