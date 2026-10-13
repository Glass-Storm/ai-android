package hk.edu.hkmu.demo.model

/**
 * One row in the "Models" section.
 *
 * A row is either **downloadable** ([url] + [sha256]) or **bundled** ([bundledAssetPath]); exactly
 * one of those shapes is used. [relativePath] is the artifact's location under the model root
 * (`filesDir/models`), which is also what the engines resolve against.
 *
 * @property label Human-readable row label.
 * @property relativePath Path under the model root, using `/` separators.
 * @property url Download URL for a downloadable row, else null.
 * @property sha256 Pinned lowercase hex digest for a downloadable row, else null.
 * @property bundledAssetPath APK asset path for a bundled row, else null.
 */
data class DemoModel(
    val label: String,
    val relativePath: String,
    val url: String? = null,
    val sha256: String? = null,
    val bundledAssetPath: String? = null
) {
    /** True for a row packaged inside the APK rather than downloaded. */
    val isBundled: Boolean get() = bundledAssetPath != null
}
