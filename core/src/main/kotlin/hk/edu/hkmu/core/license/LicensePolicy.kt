package hk.edu.hkmu.core.license

/**
 * Decides whether a model may be used for a given [UsageIntent].
 *
 * This is an **interface**: the library ships contracts only. Implementations (registry, downloader,
 * policy layer) call [evaluate] before a model is loaded or written to disk and must refuse on
 * [LicenseDecision.Restricted].
 *
 * ### Decision surface
 * - [UsageIntent.SelfUse] allows [LicenseKind.Permissive] unconditionally and
 *   [LicenseKind.NonCommercial] with a recorded [LicenseDecision.Allowed.acknowledgment]; it also
 *   permits [LicenseKind.UserProvided].
 * - [UsageIntent.Commercial] allows only [LicenseKind.Permissive]; it blocks
 *   [LicenseKind.NonCommercial], blocks [LicenseKind.UserProvided] when the declared license is not
 *   determinable, and **always** blocks [LicenseKind.UnknownLicense].
 *
 * ### UnknownLicense
 * A model whose license cannot be resolved is never silently usable: under
 * [UsageIntent.Commercial] it is always [LicenseDecision.Restricted], and under
 * [UsageIntent.SelfUse] it is allowed only with an explicit acknowledgment. Implementations must
 * read the license from a **signature-verified** manifest so a tampered manifest cannot relabel a
 * non-commercial model as permissive.
 */
interface LicensePolicy {
    /** Evaluates [license] for [intent]. */
    fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision
}
