package hk.edu.hkmu.testkit.sign

import hk.edu.hkmu.core.engine.PostProcessor
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.sign.SignClassifierRegistry
import hk.edu.hkmu.sign.classifier.SignClassifier
import hk.edu.hkmu.sign.classifier.SignClassifierFactory
import hk.edu.hkmu.sign.model.SignModelCatalog
import hk.edu.hkmu.sign.model.SignModelSpec
import hk.edu.hkmu.sign.pipeline.SignPipeline
import hk.edu.hkmu.sign.pipeline.SignPipelineConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A host-JVM (headless) harness that builds a real [SignPipeline] over a [SignClassifier] fake.
 *
 * It wires the same production composition as a host — [SignClassifierRegistry] (catalog + license
 * gate + asset validation) feeding [SignPipeline.create] — but resolves every classifier through the
 * caller-supplied [SignClassifier] via [SignClassifierRegistry.withClassifierFactory], so the full
 * segmentation → normalization → classification path runs with **no device, no native library and no
 * real model**. It uses only public `:sign` API plus this module's own fakes.
 *
 * Pair it with [landmarkStream] + [movingFrames]/[stillFrames] and drive
 * `pipeline.recognizeLandmarks(...)` to get the hub's end-to-end CI check.
 *
 * Owns its temporary model root and both collaborators; call [close] when done.
 *
 * @property registry Classifier registry the pipeline classifies through.
 * @property pipeline The assembled sign→text pipeline.
 * @property modelRoot Temporary directory holding the dummy artifact the asset validator requires.
 */
class SignPipelineHarness private constructor(
    val registry: SignClassifierRegistry,
    val pipeline: SignPipeline,
    val modelRoot: File
) : AutoCloseable {

    /** Closes the pipeline and registry, then deletes the temporary model root. Idempotent. */
    override fun close() {
        pipeline.close()
        registry.close()
        modelRoot.deleteRecursively()
    }

    companion object {
        /** Shipped classifier id used by default; its 50-frame window matches [movingFrames] defaults. */
        const val DEFAULT_CLASSIFIER_ID: String = "tgcn-wlasl-asl100"

        /**
         * Builds a [SignPipeline] whose classifier graph is [classifier] for every resolved spec.
         *
         * @param classifier Classifier installed for the resolved [SignModelSpec].
         * @param classifierId Id to serve; must exist in [catalog]. Defaults to the shipped TGCN id.
         * @param config Pipeline configuration; defaults to [SignPipelineConfig] for [classifierId].
         * @param catalog Catalog to register; defaults to [SignModelCatalog.Standard].
         * @param intent License intent; defaults to [UsageIntent.SelfUse], which admits the shipped
         *   non-commercial TGCN model (a commercial intent would be refused by the license gate).
         * @param postProcessor Optional recombination post-processor; `null` disables it.
         * @param dispatcher Worker dispatcher for classification.
         */
        @Suppress("LongParameterList")
        fun create(
            classifier: SignClassifier,
            classifierId: String = DEFAULT_CLASSIFIER_ID,
            config: SignPipelineConfig = SignPipelineConfig(classifierId = classifierId),
            catalog: SignModelCatalog = SignModelCatalog.Standard,
            intent: UsageIntent = UsageIntent.SelfUse,
            postProcessor: PostProcessor? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): SignPipelineHarness {
            val modelRoot = Files.createTempDirectory("testkit-sign-pipeline").toFile()
            val registry = SignClassifierRegistry.withClassifierFactory(
                modelRoot = modelRoot,
                classifierFactory = SignClassifierFactory { _, _ -> classifier },
                catalog = catalog,
                licensePolicy = DefaultLicensePolicy(),
                intent = intent,
                capacity = ModelRegistry.DEFAULT_CAPACITY
            )
            materializeAsset(registry.requireSpec(classifierId), modelRoot)
            val pipeline = SignPipeline.create(
                extractor = FakeLandmarkExtractor(),
                registry = registry,
                config = config,
                postProcessor = postProcessor,
                ownsExtractor = true,
                dispatcher = dispatcher
            )
            return SignPipelineHarness(registry, pipeline, modelRoot)
        }

        private fun materializeAsset(spec: SignModelSpec, modelRoot: File) {
            val directory = File(modelRoot, spec.directoryName)
            directory.mkdirs()
            File(directory, spec.modelFile).writeBytes(byteArrayOf(0))
        }
    }
}
