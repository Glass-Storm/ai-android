package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File

/**
 * Narrow internal seam over one sign-classifier graph.
 *
 * Confining every `org.tensorflow.lite.*` type to [TfliteSignClassifier] keeps Tier A tests free of
 * the native LiteRT runtime and of a real model: the engine depends only on this interface, and tests
 * inject a fake through [SignClassifierFactory].
 */
internal interface SignClassifier : AutoCloseable {
    /**
     * Runs the classifier over a fully-assembled window tensor and returns raw `[classCount]` logits.
     *
     * @param input flattened model input (length == the graph's input element count).
     */
    fun classify(input: FloatArray): FloatArray

    /** Releases the underlying interpreter. Idempotent. */
    override fun close() {}
}

/** Builds a [SignClassifier] for [spec] from its resolved [modelFile]. */
internal fun interface SignClassifierFactory {
    /** @throws hk.edu.hkmu.core.error.AiAndroidException when the graph cannot be loaded. */
    fun create(spec: SignModelSpec, modelFile: File): SignClassifier
}
