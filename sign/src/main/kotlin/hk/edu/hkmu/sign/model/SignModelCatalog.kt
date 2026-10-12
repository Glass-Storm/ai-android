package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Maps a classifier id (or sign language) to the [SignModelSpec] that serves it.
 *
 * The catalog is data, not code: hosts can supply their own list and the resolver still behaves the
 * same. [requireClassifier] fails with the typed [AiAndroidException.ModelNotAvailable] — not a
 * generic error — so a caller asking for a language or model the build does not ship gets an
 * actionable failure.
 *
 * @property specs Declared classifiers; the first match for a query wins.
 */
class SignModelCatalog(private val specs: List<SignModelSpec> = defaultSpecs()) {

    /** The classifier registered under [classifierId], or `null` when absent. */
    fun forClassifierId(classifierId: String): SignModelSpec? = specs.firstOrNull { it.classifierId == classifierId }

    /** The first classifier serving [language], or `null` when the language is unshipped. */
    fun forLanguage(language: Language): SignModelSpec? = specs.firstOrNull { it.language == language }

    /** The classifier registered under [classifierId]; throws typed `ModelNotAvailable` when absent. */
    fun requireClassifier(classifierId: String): SignModelSpec =
        forClassifierId(classifierId) ?: throw AiAndroidException.ModelNotAvailable(classifierId)

    /** Every declared classifier, in declaration order. */
    fun all(): List<SignModelSpec> = specs

    companion object {
        /** The shipped catalog: TGCN isolated-word ASL and SignBridge ASL fingerspelling. */
        val Standard: SignModelCatalog = SignModelCatalog()

        /** The shipped classifier list (both serve ASL; CSL is BYO-only). */
        fun defaultSpecs(): List<SignModelSpec> = listOf(TgcnWlaslIsolatedAsl, SignBridgeFingerspellingAsl)
    }
}
