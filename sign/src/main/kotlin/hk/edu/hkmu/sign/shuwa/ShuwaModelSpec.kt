package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.sign.model.SignLanguages

/**
 * Declarative identity of one Shuwa HKSL translator.
 *
 * The spec names the model, the sign language it serves and its licence, so a host can record what it
 * provisioned without the engine hardcoding either. Nothing is shipped: the host supplies the
 * `.tflite` translator and the `SKS1` KNN table, and passes this spec to [ShuwaKnnIndex.load] with
 * the licence that data actually carries. Google's sample embeddings have no declared upstream
 * licence, so a host that provisions them should use [ModelLicense] kind `UserProvided` /
 * `UnknownLicense`.
 *
 * @property license Licence attached to the host-provisioned artifacts.
 * @property classifierId Stable id used in logs and diagnostics.
 * @property language Sign language served; defaults to [SignLanguages.Hksl].
 * @property windowFrames Frames the translator consumes per window.
 * @property embeddingDimensions Dimensions of the translator's embedding.
 */
data class ShuwaModelSpec(
    val license: ModelLicense,
    val classifierId: String = DEFAULT_CLASSIFIER_ID,
    val language: Language = SignLanguages.Hksl,
    val windowFrames: Int = ShuwaSpec.WINDOW_FRAMES,
    val embeddingDimensions: Int = ShuwaSpec.EMBEDDING_DIMENSIONS
) {
    companion object {
        /** Default id for the host-provisioned HKSL translator. */
        const val DEFAULT_CLASSIFIER_ID: String = "shuwa-hksl"
    }
}
