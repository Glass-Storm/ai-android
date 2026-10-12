@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.models.manifest.ModelDescriptor

/**
 * The input/output contract a host declares for a bring-your-own classifier.
 *
 * A BYO artifact has no vendor spec, so the host supplies the metadata the engine needs (window,
 * label table, tensor layout). The license is NOT declared here: it is pinned to
 * `LicenseKind.UnknownLicense` by the `UserProvidedModel` trust path regardless of the host's claim.
 */
@Suppress("LongParameterList")
data class SignClassifierContract(
    val language: Language,
    val windowFrames: Int,
    val inputLayout: SignInputLayout,
    val classCount: Int,
    val labels: List<String>,
    val modelFile: String
) {
    /** Builds the [SignModelSpec] for a registered BYO artifact. Internal: only the registry mints. */
    internal fun toSpec(descriptor: ModelDescriptor): SignModelSpec = SignModelSpec(
        classifierId = descriptor.id,
        language = language,
        license = descriptor.license,
        directoryName = descriptor.id,
        modelFile = modelFile,
        windowFrames = windowFrames,
        inputLayout = inputLayout,
        classCount = classCount,
        labels = labels,
        sourceUrl = descriptor.urls.first(),
        shipped = false
    )
}

/**
 * Ready-made BYO contracts for the two slots the plan exposes.
 *
 * Neither ships a model: a host registers its own artifact through
 * [hk.edu.hkmu.sign.SignClassifierRegistry.registerByo], which pins the license to `UnknownLicense`
 * and therefore blocks it under `UsageIntent.Commercial`.
 */
object SignByoContracts {
    /** CSL slot (no CSL model is shipped). Defaults to the TGCN-style 55-keypoint 100-class contract. */
    val Csl: SignClassifierContract = SignClassifierContract(
        language = SignLanguages.Csl,
        windowFrames = 50,
        inputLayout = SignInputLayout.TransposedNodes(nodes = 55, dimsPerNode = 2),
        classCount = 100,
        labels = List(100) { "csl-$it" },
        modelFile = "model.tflite"
    )

    /** ASL fingerspelling slot for a host's own alphabet model (the shipped one is MIT). */
    val Fingerspelling: SignClassifierContract = SignClassifierContract(
        language = SignLanguages.Asl,
        windowFrames = 30,
        inputLayout = SignInputLayout.FrameFeatures(featuresPerFrame = 63),
        classCount = 28,
        labels = ('A'..'Z').map { it.toString() } + listOf("del", "space"),
        modelFile = "model.tflite"
    )
}
