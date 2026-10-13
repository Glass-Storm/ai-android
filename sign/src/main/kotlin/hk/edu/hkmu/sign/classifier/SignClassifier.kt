package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File

/**
 * Low-level seam over one sign-classifier graph: window tensor in, raw logits out.
 *
 * Confining every `org.tensorflow.lite.*` type to [TfliteSignClassifier] keeps Tier A tests free of
 * the native LiteRT runtime and of a real model. It is **public** so a host — or a headless CI via
 * `:testkit` — can supply its own classifier (e.g. a deterministic fake) and drive the full
 * segmentation → normalization → classification path through
 * [hk.edu.hkmu.sign.pipeline.SignPipeline.recognizeLandmarks] with no device and no native library.
 * A classifier produced by a caller-supplied [SignClassifierFactory] is installed through the public
 * [hk.edu.hkmu.sign.SignClassifierRegistry] constructor that takes such a factory.
 *
 * This is a `fun interface`, so a caller may also provide a bare lambda:
 * `SignClassifier { input -> logits }`.
 */
fun interface SignClassifier : AutoCloseable {
    /**
     * Runs the classifier over a fully-assembled window tensor and returns raw `[classCount]` logits.
     *
     * @param input flattened model input (length == the graph's input element count).
     */
    fun classify(input: FloatArray): FloatArray

    /** Releases the underlying interpreter. Idempotent. */
    override fun close() {}
}

/**
 * Builds a [SignClassifier] for `spec` from its resolved `modelFile`.
 *
 * Public so hosts and headless tests can inject a classifier graph (typically a fake) into a
 * [hk.edu.hkmu.sign.SignClassifierRegistry] without the LiteRT native runtime. The default
 * (production) factory is [TfliteSignClassifier.Factory], used by the registry's shipped
 * constructor; this seam does not change TFLite behavior.
 */
fun interface SignClassifierFactory {
    /** @throws hk.edu.hkmu.core.error.AiAndroidException when the graph cannot be loaded. */
    fun create(spec: SignModelSpec, modelFile: File): SignClassifier
}
