package hk.edu.hkmu.core.license

/** Outcome of a [LicensePolicy] evaluation. */
sealed interface LicenseDecision {
    /**
     * Use is permitted.
     *
     * @property acknowledgment Optional text the host must surface (e.g. the NC notice under
     *   SelfUse).
     */
    data class Allowed(val acknowledgment: String? = null) : LicenseDecision

    /**
     * Use is refused.
     *
     * @property reason Human-readable explanation.
     * @property license The offending license.
     * @property intent The attempted use.
     */
    data class Restricted(
        val reason: String,
        val license: ModelLicense,
        val intent: UsageIntent
    ) : LicenseDecision
}
