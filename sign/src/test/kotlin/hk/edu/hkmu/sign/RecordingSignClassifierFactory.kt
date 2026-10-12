package hk.edu.hkmu.sign

import hk.edu.hkmu.sign.classifier.SignClassifier
import hk.edu.hkmu.sign.classifier.SignClassifierFactory
import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File

/**
 * Deterministic in-memory [SignClassifier] for Tier A tests.
 *
 * Returns [logits] for every input, records the inputs it saw, and tracks [closed] so the engine's
 * lazy-acquire/close lifecycle can be asserted with no LiteRT native library.
 */
internal class FakeSignClassifier(private val logits: FloatArray) : SignClassifier {

    /** Inputs passed to [classify], copied on capture. */
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
}

/** A [SignClassifierFactory] that hands out [FakeSignClassifier]s and counts invocations. */
internal class RecordingSignClassifierFactory(private val logits: FloatArray) : SignClassifierFactory {

    /** Every classifier created, in creation order. */
    val created: MutableList<FakeSignClassifier> = mutableListOf()

    /** How many times the factory was asked to build a model (0 proves the loader never ran). */
    val createCount: Int get() = created.size

    override fun create(spec: SignModelSpec, modelFile: File): SignClassifier =
        FakeSignClassifier(logits).also { created += it }
}
