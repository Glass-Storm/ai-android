package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.sign.SignClassifierRegistry
import hk.edu.hkmu.sign.classifier.SignClassifierHandle
import hk.edu.hkmu.sign.classifier.SignSoftmax
import hk.edu.hkmu.sign.landmark.ClassifierInputSpec
import hk.edu.hkmu.sign.landmark.LandmarkNormalizer
import hk.edu.hkmu.sign.segment.SignWindow

/** The winning label and its softmax confidence for one classified window. */
internal data class SignClassification(
    val label: String,
    val confidence: Float
)

/**
 * Seam over the classification stage of [SignPipeline].
 *
 * It turns one segmenter [SignWindow] into a [SignClassification]. Keeping it an interface lets a
 * host JVM test substitute a fake and exercise the whole pipeline (extraction-excluded) wiring with
 * no registry, no LiteRT and no MediaPipe native.
 */
internal interface SignClassifierStage : AutoCloseable {

    /** Classifies the landmark window. */
    fun classify(window: SignWindow): SignClassification

    /** Releases any pinned classifier. Idempotent. */
    override fun close() {}
}

/**
 * Production classification stage: the todo-21 registry (license-gated, lazily acquired) plus the
 * todo-19 normalization contract, composed exactly as the other engines do.
 *
 * - The window's [hk.edu.hkmu.sign.landmark.LandmarkFrame]s are normalized through the todo-19
 *   [LandmarkNormalizer] into the classifier's input tensor (shape derived from [inputSpec]).
 * - The graph is run through the classifier handle owned by the shared [SignClassifierRegistry],
 *   acquired lazily on the first window so an idle pipeline loads no native interpreter; the license
 *   gate already ran at pipeline construction.
 * - Logits are turned into a label + confidence by the todo-21 [SignSoftmax].
 *
 * @param registry Registry that owns the classifier's lifetime (never closed here).
 * @param classifierId Model to acquire on first use.
 * @param inputSpec Input contract derived from the model's `SignModelSpec`.
 */
internal class RegistrySignClassifierStage(
    private val registry: SignClassifierRegistry,
    private val classifierId: String,
    private val inputSpec: ClassifierInputSpec
) : SignClassifierStage {

    private val normalizer = LandmarkNormalizer(inputSpec)

    private val leaseDelegate: Lazy<ModelLease<SignClassifierHandle>> =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) { registry.acquire(classifierId) }

    override fun classify(window: SignWindow): SignClassification {
        val tensor = normalizer.normalize(window.frames)
        val handle = leaseDelegate.value.handle
        val probabilities = SignSoftmax.softmax(handle.classify(tensor.data))
        val index = SignSoftmax.argMax(probabilities)
        return SignClassification(handle.spec.labelFor(index), probabilities[index])
    }

    override fun close() {
        if (leaseDelegate.isInitialized()) {
            leaseDelegate.value.close()
        }
    }
}
