package hk.edu.hkmu.sign

import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.engine.SignEngine
import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.sign.classifier.SignClassifierHandle
import hk.edu.hkmu.sign.classifier.SignSoftmax
import hk.edu.hkmu.sign.classifier.SignWindowAdapter
import hk.edu.hkmu.sign.model.SignModelCatalog
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [SignEngine] backed by the shipped LiteRT/TFLite classifier runtime.
 *
 * ### Model resolution + license
 * Construction resolves [SignConfig.classifierId] against the [SignClassifierRegistry] and evaluates
 * the license gate immediately, so an unresolved id raises [AiAndroidException.ModelNotAvailable] and
 * a policy-blocked model raises [AiAndroidException.LicenseRestricted] before any native work. The
 * registry's own gate re-runs at load time, so the native interpreter is never constructed for a
 * blocked model.
 *
 * ### Windows
 * The window length is the **model's** contract ([SignModelSpec.windowFrames], e.g. 50 for TGCN, 30
 * for SignBridge fingerspelling); [SignConfig.stride] controls how far the window advances between
 * predictions, so consecutive windows overlap when `stride < windowFrames`. One [SignPrediction] is
 * emitted per completed window and the flow completes with the input flow; cancelling collection
 * discards the in-progress window (per the core [SignEngine] contract). The classifier runs on
 * [dispatcher] (a worker thread), matching the core backpressure contract.
 *
 * ### Lifecycle
 * The classifier is acquired lazily on first collection and pinned until [close], so repeated
 * `recognize` calls reuse one interpreter.
 */
class TfliteSignEngine private constructor(
    private val config: SignConfig,
    private val registry: SignClassifierRegistry,
    private val classifierId: String,
    private val dispatcher: CoroutineDispatcher,
    private val ownsRegistry: Boolean
) : SignEngine, AutoCloseable {

    private val spec = registry.requireSpec(classifierId)

    private val leaseDelegate: Lazy<ModelLease<SignClassifierHandle>> =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) { registry.acquire(classifierId) }

    init {
        registry.ensureAllowed(classifierId)
    }

    override fun recognize(frames: Flow<SignFrame>): Flow<SignPrediction> = flow {
        val handle = leaseDelegate.value.handle
        val windowFrames = spec.windowFrames
        val stride = config.stride.coerceIn(MIN_STRIDE, windowFrames)
        val window = ArrayDeque<SignFrame>(windowFrames)
        frames.collect { frame ->
            window.addLast(frame)
            if (window.size < windowFrames) {
                return@collect
            }
            emit(predict(handle, window))
            repeat(stride) { window.removeFirst() }
        }
    }.flowOn(dispatcher)

    /** Releases the pinned classifier and, when this engine created the registry, its resources. */
    override fun close() {
        if (leaseDelegate.isInitialized()) {
            leaseDelegate.value.close()
        }
        if (ownsRegistry) {
            registry.close()
        }
    }

    private fun predict(handle: SignClassifierHandle, window: ArrayDeque<SignFrame>): SignPrediction {
        val frames = window.toList()
        val tensor = SignWindowAdapter.assemble(frames, spec)
        val probabilities = SignSoftmax.softmax(handle.classify(tensor))
        val index = SignSoftmax.argMax(probabilities)
        return SignPrediction(
            label = spec.labelFor(index),
            confidence = probabilities[index],
            windowStartMillis = frames.first().timestampMillis,
            windowEndMillis = frames.last().timestampMillis
        )
    }

    companion object {
        private const val MIN_STRIDE = 1

        /**
         * Creates an engine reading model artifacts under [modelRoot].
         *
         * @param registry Optional pre-built registry (e.g. one carrying a BYO CSL model); when `null`
         *   the engine builds and owns its own. A caller-supplied registry is never closed by the
         *   engine.
         * @throws AiAndroidException.ModelNotAvailable when the classifier id is unresolved.
         * @throws AiAndroidException.LicenseRestricted when the license gate refuses the model.
         */
        @Suppress("LongParameterList")
        fun create(
            config: SignConfig,
            modelRoot: File,
            catalog: SignModelCatalog = SignModelCatalog.Standard,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            registry: SignClassifierRegistry? = null
        ): TfliteSignEngine {
            val effective = registry ?: SignClassifierRegistry(modelRoot, catalog, licensePolicy, intent)
            return TfliteSignEngine(
                config = config,
                registry = effective,
                classifierId = resolveClassifierId(config, effective),
                dispatcher = Dispatchers.Default,
                ownsRegistry = registry == null
            )
        }

        /** Test seam: engine over an injected [registry] (fake classifier factory, no native). */
        internal fun forTesting(
            config: SignConfig,
            registry: SignClassifierRegistry,
            classifierId: String = config.classifierId,
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        ): TfliteSignEngine = TfliteSignEngine(
            config = config,
            registry = registry,
            classifierId = classifierId,
            dispatcher = dispatcher,
            ownsRegistry = false
        )

        private fun resolveClassifierId(config: SignConfig, registry: SignClassifierRegistry): String {
            if (config.classifierId.isNotBlank()) {
                return config.classifierId
            }
            return registry.specForLanguage(config.language)?.classifierId
                ?: throw AiAndroidException.ModelNotAvailable(config.language.tag)
        }
    }
}
