package hk.edu.hkmu.core.license

/**
 * Production [LicensePolicy]: the single, side-effect-free decision surface every shared load path
 * (the model registry and the downloader) consults before a model is instantiated or a byte is
 * written.
 *
 * The [ModelLicense] it evaluates always comes from a trust-gated source — a signature-verified
 * vendor manifest, or the bring-your-own registration that pins the license to
 * [LicenseKind.UnknownLicense]. The policy never reads files or the network; it is a pure function of
 * [ModelLicense] and [UsageIntent], so the registry and the downloader inject the exact same object
 * and a caller cannot widen a decision by choosing a different path.
 *
 * ### Decision surface
 * - [UsageIntent.SelfUse]: [LicenseKind.Permissive] is allowed unconditionally;
 *   [LicenseKind.NonCommercial] is allowed **with a non-null acknowledgment** recorded on the
 *   returned [LicenseDecision.Allowed] (the model's own notice, or a synthesized NC notice when it
 *   has none); [LicenseKind.UserProvided] and [LicenseKind.UnknownLicense] are likewise allowed but
 *   always carry an explicit acknowledgment, so an unclassified model is never used silently.
 * - [UsageIntent.Commercial]: only [LicenseKind.Permissive] is allowed. [LicenseKind.NonCommercial]
 *   (CC-BY-NC, and dataset-derived NC models such as the WLASL/C-UDA sign classifier),
 *   [LicenseKind.UserProvided], and [LicenseKind.UnknownLicense] are refused.
 *   [LicenseKind.UnknownLicense] is **always** refused, so a model whose license cannot be
 *   determined can never ship in a commercial product.
 */
class DefaultLicensePolicy : LicensePolicy {
    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision = when (intent) {
        UsageIntent.SelfUse -> decideSelfUse(license)
        UsageIntent.Commercial -> decideCommercial(license)
    }

    private fun decideSelfUse(license: ModelLicense): LicenseDecision = when (license.kind) {
        LicenseKind.Permissive -> LicenseDecision.Allowed(license.notice)
        LicenseKind.NonCommercial -> LicenseDecision.Allowed(license.notice ?: NON_COMMERCIAL_ACKNOWLEDGMENT)
        LicenseKind.UserProvided -> LicenseDecision.Allowed(license.notice ?: USER_PROVIDED_ACKNOWLEDGMENT)
        LicenseKind.UnknownLicense -> LicenseDecision.Allowed(license.notice ?: UNKNOWN_LICENSE_ACKNOWLEDGMENT)
    }

    private fun decideCommercial(license: ModelLicense): LicenseDecision = when (license.kind) {
        LicenseKind.Permissive -> LicenseDecision.Allowed(license.notice)
        LicenseKind.NonCommercial -> restricted(license, UsageIntent.Commercial)
        LicenseKind.UserProvided -> restricted(license, UsageIntent.Commercial)
        LicenseKind.UnknownLicense -> restricted(license, UsageIntent.Commercial)
    }

    private fun restricted(license: ModelLicense, intent: UsageIntent): LicenseDecision.Restricted =
        LicenseDecision.Restricted(
            reason = "license ${license.kind.name} is not permitted for ${intent.name}",
            license = license,
            intent = intent
        )

    companion object {
        /** Recorded when a non-commercial model carries no notice of its own. */
        const val NON_COMMERCIAL_ACKNOWLEDGMENT =
            "Non-commercial model: personal or research use only; commercial use is prohibited."

        /** Recorded for a host-provided model whose declared license the library cannot verify. */
        const val USER_PROVIDED_ACKNOWLEDGMENT =
            "Host-provided model: the host declares its license; the library cannot verify it."

        /** Recorded for a model whose license could not be determined. */
        const val UNKNOWN_LICENSE_ACKNOWLEDGMENT =
            "Model license could not be determined; use is at your own risk."
    }
}
