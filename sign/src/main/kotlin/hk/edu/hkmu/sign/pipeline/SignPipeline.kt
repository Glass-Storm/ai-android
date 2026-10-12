package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.SignClassifierRegistry
import hk.edu.hkmu.sign.landmark.LandmarkExtractor
import hk.edu.hkmu.sign.landmark.LandmarkFrame
import hk.edu.hkmu.sign.landmark.VideoFrame
import hk.edu.hkmu.sign.model.SignModelSpec
import hk.edu.hkmu.sign.segment.SignSegmenter
import hk.edu.hkmu.sign.segment.SignSegmenterConfig
import hk.edu.hkmu.sign.segment.SignWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * End-to-end sign→text pipeline: the composition facade over the sign module's stages.
 *
 * ### Composition
 * ```
 * decoded frames (VideoFrame)
 *   -> MediaPipe extraction        (todo 19, LandmarkExtractor)
 *   -> transition-gated segmentation (todo 20, SignSegmenter)
 *   -> per-window normalization    (todo 19, LandmarkNormalizer)
 *   -> classification              (todo 21, SignClassifierRegistry + SignSoftmax)
 *   -> optional recombination      (todo 17, PostProcessor; lazy + opt-out)
 *   -> Flow<SignResult>
 * ```
 * Each stage is the already-shipped type; this class only wires them and defines the result/ordering
 * semantics. It never opens a camera and never re-implements extraction, segmentation or
 * classification.
 *
 * ### Output ceiling: letters / isolated signs — NOT sentence translation
 * Emitted [SignResult]s are **isolated** recognitions (a fingerspelled letter, `del`/`space`, or one
 * isolated sign word). The optional small-LLM post-processor (todo 17) may recombine a confident
 * result's text, but the library performs **no continuous sign-language → sentence translation** and
 * makes no such claim. This capability is `SignEngine`'s letter/isolated-word sibling, not an ASR
 * replacement; it does not depend on the ASR module.
 *
 * ### Relationship to `TfliteSignEngine` (todo 21)
 * `TfliteSignEngine` is the lower-level core `SignEngine` adapter: it consumes caller-supplied,
 * already-normalized `SignFrame`s and emits `SignPrediction`s at a stride-driven window boundary. A
 * host that owns its own extraction/windowing calls it directly (it is the DI
 * `EngineFactory.createSignEngine` slot). [SignPipeline] is the higher-level single entry for the
 * full frame→text path; hosts that have decoded video frames call [SignPipeline]. Both share the
 * same [SignClassifierRegistry] and license gate; neither re-implements the other.
 *
 * ### Ordering, cancellation, backpressure
 * Results are emitted strictly in window order (the flow preserves the segmenter's ordering).
 * Cancelling collection stops the pipeline at the current stage. Classification runs on [dispatcher].
 *
 * @param extractor Caller-supplied frame extractor (todo 19); owned by the pipeline only when
 *   [ownsExtractor] is true.
 * @param ownsExtractor When true [close] also closes [extractor].
 * @param segmenter Transition-gated window producer (todo 20).
 * @param classifierStage Classification stage (todo 21).
 * @param postProcessor Optional recombination post-processor (todo 17); `null` disables it.
 * @param config Pipeline configuration.
 * @param dispatcher Worker dispatcher for extraction + classification.
 */
@Suppress("LongParameterList")
class SignPipeline internal constructor(
    private val extractor: LandmarkExtractor,
    private val ownsExtractor: Boolean,
    private val segmenter: SignSegmenter,
    private val classifierStage: SignClassifierStage,
    private val postProcessor: PostProcessor?,
    private val config: SignPipelineConfig,
    private val dispatcher: CoroutineDispatcher
) : AutoCloseable {

    /**
     * Recognizes signs from decoded [frames] end to end.
     *
     * @throws AiAndroidException.MalformedFrame when the extractor reports a frame the classifier
     *   contract cannot satisfy (e.g. a missing hand).
     */
    fun recognize(frames: Flow<VideoFrame>): Flow<SignResult> {
        return recognizeLandmarks(frames.map { frame -> extractor.extract(frame) })
    }

    /**
     * Extraction-free composition seam: the pipeline from assembled landmark frames onward.
     *
     * Exposed for host JVM tests and hosts that extract landmarks themselves; [recognize] is the
     * production entry. The MediaPipe native extraction boundary is device-only, so Tier A exercises
     * this seam with synthetic landmarks (mirroring how `sign.landmark` is tested).
     */
    internal fun recognizeLandmarks(frames: Flow<LandmarkFrame>): Flow<SignResult> {
        return segmenter.segment(frames).map { window -> classify(window) }.flowOn(dispatcher)
    }

    /** Releases the pinned classifier and, when owned, the extractor. Idempotent. */
    override fun close() {
        classifierStage.close()
        if (ownsExtractor) {
            extractor.close()
        }
    }

    private suspend fun classify(window: SignWindow): SignResult {
        val classification = classifierStage.classify(window)
        val lowConfidence = classification.confidence < config.confidenceThreshold
        val processor = postProcessor
        var recombined = false
        val text = if (processor != null && !lowConfidence && config.recombinationEnabled) {
            recombined = true
            recombine(processor, classification.label)
        } else {
            classification.label
        }
        return SignResult(
            label = classification.label,
            text = text,
            confidence = classification.confidence,
            windowIndex = window.index,
            windowStartMillis = window.startMillis,
            windowEndMillis = window.endMillis,
            lowConfidence = lowConfidence,
            recombined = recombined
        )
    }

    private suspend fun recombine(processor: PostProcessor, label: String): String {
        val builder = StringBuilder()
        processor.process(label).collect { piece -> builder.append(piece) }
        return builder.toString().ifEmpty { label }
    }

    companion object {
        private const val MIN_STRIDE = 1

        /**
         * Builds the pipeline for the classifier named in [config].
         *
         * The classifier is resolved and license-gated immediately ([AiAndroidException.ModelNotAvailable]
         * / [AiAndroidException.LicenseRestricted] before any native work), and the segmenter window is
         * taken from the model's contract so normalization always receives a full window.
         *
         * @param extractor Frame extractor (todo 19). [ownsExtractor] controls its lifecycle.
         * @param registry Registry owning classifier lifetime (todo 21); never closed by the pipeline.
         * @param config Pipeline configuration.
         * @param postProcessor Optional recombination post-processor (todo 17); allows opt-out.
         * @param ownsExtractor When true the pipeline closes [extractor] on [close].
         * @param dispatcher Worker dispatcher (default `Dispatchers.Default`).
         * @throws AiAndroidException.PipelineMisconfigured when the classifier's input contract is not
         *   expressible by the 2D landmark extractor.
         */
        @Suppress("LongParameterList")
        fun create(
            extractor: LandmarkExtractor,
            registry: SignClassifierRegistry,
            config: SignPipelineConfig,
            postProcessor: PostProcessor? = null,
            ownsExtractor: Boolean = false,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): SignPipeline {
            val spec = resolveSpec(registry, config)
            registry.ensureAllowed(spec.classifierId)
            val inputSpec = spec.toClassifierInputSpec()
            val segmenter = SignSegmenter(
                SignSegmenterConfig(
                    windowSize = spec.windowFrames,
                    stride = config.stride.coerceIn(MIN_STRIDE, spec.windowFrames),
                    motionThreshold = config.motionThreshold
                )
            )
            val stage = RegistrySignClassifierStage(registry, spec.classifierId, inputSpec)
            return SignPipeline(extractor, ownsExtractor, segmenter, stage, postProcessor, config, dispatcher)
        }

        private fun resolveSpec(registry: SignClassifierRegistry, config: SignPipelineConfig): SignModelSpec {
            if (config.classifierId.isNotBlank()) {
                return registry.requireSpec(config.classifierId)
            }
            return registry.specForLanguage(config.language)
                ?: throw AiAndroidException.ModelNotAvailable(config.language.tag)
        }
    }
}
