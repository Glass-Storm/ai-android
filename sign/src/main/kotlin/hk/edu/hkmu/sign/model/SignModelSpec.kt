package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.license.ModelLicense

/**
 * How a classifier's input tensor is arranged from a window of per-frame landmark vectors.
 *
 * The two layouts cover every shipped and BYO classifier in this module, and each is a pure data
 * decision so the same window-assembly code serves them (see `SignWindowAdapter`).
 */
sealed interface SignInputLayout {
    /**
     * `[1, windowFrames, featuresPerFrame]` — one row per frame, feature-major within the row.
     *
     * Used by the SignBridge fingerspelling CNN: `[1, 30, 63]`, i.e. 30 frames of 21 hand landmarks
     * `(x, y, z)`.
     */
    data class FrameFeatures(val featuresPerFrame: Int) : SignInputLayout

    /**
     * `[1, dimsPerNode * windowFrames, nodes]` — the onnx2tf-transposed TGCN contract.
     *
     * For each node the first `windowFrames` features are its `x` over the window, then the next
     * `windowFrames` its `y`, and so on per coordinate. The shipped TGCN ships as `[1, 100, 55]`:
     * 2 coords × 50 frames over 55 keypoints.
     */
    data class TransposedNodes(
        val nodes: Int,
        val dimsPerNode: Int = DEFAULT_NODES_DIMS_PER_NODE
    ) : SignInputLayout
}

/** `(x, y, z)` per node when a layout does not override it. */
internal const val DEFAULT_NODES_DIMS_PER_NODE: Int = 2

/**
 * Declarative description of one sign classifier, independent of where its file lives.
 *
 * A spec is the single source of truth for a classifier's input contract, output classes and license,
 * so the engine, the license gate and the asset validator all agree without per-model code. Shipped
 * specs come from [SignModelCatalog]; a BYO model becomes a spec through
 * `SignClassifierRegistry.registerByo`.
 *
 * @property classifierId Stable registry id (also the `ModelNotAvailable` id when unresolved).
 * @property language Sign language the classifier serves.
 * @property license Per-model license evaluated by the shared license policy before load.
 * @property directoryName Directory under the caller's model root holding the artifact.
 * @property modelFile Artifact file name inside [directoryName].
 * @property windowFrames Number of consecutive frames the graph consumes per window.
 * @property inputLayout Tensor arrangement assembling the window into the graph input.
 * @property classCount Number of output logits.
 * @property labels Human-readable label per output index (may be shorter than [classCount]).
 * @property sourceUrl Provenance URL of the artifact (vendor release or BYO download).
 * @property shipped True for a vendor model declared by [SignModelCatalog], false for a BYO model.
 */
@Suppress("LongParameterList")
data class SignModelSpec(
    val classifierId: String,
    val language: Language,
    val license: ModelLicense,
    val directoryName: String,
    val modelFile: String,
    val windowFrames: Int,
    val inputLayout: SignInputLayout,
    val classCount: Int,
    val labels: List<String>,
    val sourceUrl: String,
    val shipped: Boolean
) {
    /** Total flattened input element count for one window. */
    val inputElements: Int = when (inputLayout) {
        is SignInputLayout.FrameFeatures -> windowFrames * inputLayout.featuresPerFrame
        is SignInputLayout.TransposedNodes -> windowFrames * inputLayout.dimsPerNode * inputLayout.nodes
    }

    /** The label for output [index], falling back to a stable synthetic label when not declared. */
    fun labelFor(index: Int): String = labels.getOrElse(index) { "$classifierId#$index" }
}
