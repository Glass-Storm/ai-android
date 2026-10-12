package hk.edu.hkmu.sign

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelLease
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.models.manifest.UserProvidedModelRegistrar
import hk.edu.hkmu.sign.classifier.SignClassifierFactory
import hk.edu.hkmu.sign.classifier.SignClassifierHandle
import hk.edu.hkmu.sign.classifier.TfliteSignClassifier
import hk.edu.hkmu.sign.model.SignClassifierContract
import hk.edu.hkmu.sign.model.SignModelAssets
import hk.edu.hkmu.sign.model.SignModelCatalog
import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File

/**
 * Registry of sign classifiers: catalog lookup, BYO registration, license gate and lifecycle.
 *
 * It is a thin composition over the shared core [ModelRegistry] (todo 9) and the license policy (todo
 * 25), so **every** model load — shipped or BYO — passes the same `LicensePolicy` gate before the
 * LiteRT interpreter is ever constructed. That is what makes the typed failures uniform:
 *
 * - an unregistered classifier id (e.g. `csl` with no BYO model) -> [AiAndroidException.ModelNotAvailable];
 * - a non-commercial model (WLASL-derived TGCN) or a BYO (`UnknownLicense`) model under
 *   `UsageIntent.Commercial` -> [AiAndroidException.LicenseRestricted].
 *
 * The public constructor wires the shipped single runtime ([TfliteSignClassifier]); the `internal`
 * primary constructor takes a [SignClassifierFactory] so host tests can inject a fake and exercise the
 * resolution + gating paths with no native library.
 *
 * @param modelRoot Directory under which each spec's `directoryName` holds its artifact.
 * @param intent How the host intends to use models; drives the license decision.
 */
class SignClassifierRegistry internal constructor(
    private val modelRoot: File,
    catalog: SignModelCatalog,
    private val licensePolicy: LicensePolicy,
    val intent: UsageIntent,
    private val factory: SignClassifierFactory,
    capacity: Int
) : AutoCloseable {

    /** Public entry point: the shipped LiteRT factory, standard catalog and `SelfUse` default. */
    constructor(
        modelRoot: File,
        catalog: SignModelCatalog = SignModelCatalog.Standard,
        licensePolicy: LicensePolicy = DefaultLicensePolicy(),
        intent: UsageIntent = UsageIntent.SelfUse,
        capacity: Int = ModelRegistry.DEFAULT_CAPACITY
    ) : this(modelRoot, catalog, licensePolicy, intent, TfliteSignClassifier.Factory(), capacity)

    private val registrar = UserProvidedModelRegistrar()
    private val specs = LinkedHashMap<String, SignModelSpec>()
    private val registry = ModelRegistry<SignClassifierHandle>(
        loader = { registration -> load(registration) },
        licensePolicy = licensePolicy,
        intent = intent,
        capacity = capacity
    )

    init {
        catalog.all().forEach { register(it) }
    }

    /** Registers [spec] (shipped or BYO) under its `classifierId`, replacing any prior entry. */
    fun register(spec: SignModelSpec) {
        specs[spec.classifierId] = spec
        registry.register(ModelRegistration(spec.classifierId, spec.license))
    }

    /**
     * Registers a host-supplied classifier through the [UserProvidedModel] trust path.
     *
     * The license is pinned to `UnknownLicense` by [UserProvidedModelRegistrar] regardless of the
     * host's declared token, so a BYO model can never smuggle a permissive license past the gate.
     *
     * @return the derived [SignModelSpec] (license `UnknownLicense`, `shipped = false`).
     */
    fun registerByo(model: UserProvidedModel, contract: SignClassifierContract): SignModelSpec {
        val descriptor = registrar.register(model)
        val spec = contract.toSpec(descriptor)
        register(spec)
        return spec
    }

    /** The spec registered under [classifierId], or `null` when absent. */
    fun specFor(classifierId: String): SignModelSpec? = specs[classifierId]

    /** The first spec serving [language], or `null` when unshipped. */
    fun specForLanguage(language: Language): SignModelSpec? = specs.values.firstOrNull { it.language == language }

    /** The spec under [classifierId]. @throws AiAndroidException.ModelNotAvailable when absent. */
    fun requireSpec(classifierId: String): SignModelSpec =
        specs[classifierId] ?: throw AiAndroidException.ModelNotAvailable(classifierId)

    /**
     * Evaluates the license gate for [classifierId] without loading the model.
     *
     * @throws AiAndroidException.ModelNotAvailable when [classifierId] is unregistered.
     * @throws AiAndroidException.LicenseRestricted when the license forbids the registry's intent.
     */
    fun ensureAllowed(classifierId: String) {
        val spec = requireSpec(classifierId)
        val decision = licensePolicy.evaluate(spec.license, intent)
        if (decision is LicenseDecision.Restricted) {
            throw AiAndroidException.LicenseRestricted(classifierId, spec.license.kind, intent, decision.reason)
        }
    }

    /** Makes [classifierId] resident and pinned, gated by the license policy. */
    internal fun acquire(classifierId: String): ModelLease<SignClassifierHandle> = registry.acquire(classifierId)

    /** Unloads every idle, resident model. Idempotent. */

    /** Unloads every idle, resident model. Idempotent. */
    override fun close() {
        specs.keys.toList().forEach { classifierId -> runCatching { registry.unload(classifierId) } }
    }

    private fun load(registration: ModelRegistration): SignClassifierHandle {
        val spec = specs[registration.modelId]
            ?: throw AiAndroidException.ModelNotAvailable(registration.modelId)
        val modelFile = SignModelAssets.ensurePresent(
            registration.modelId,
            File(modelRoot, spec.directoryName),
            spec.modelFile
        )
        return SignClassifierHandle(spec, factory.create(spec, modelFile))
    }
}
