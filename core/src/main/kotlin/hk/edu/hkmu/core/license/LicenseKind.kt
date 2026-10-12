package hk.edu.hkmu.core.license

/**
 * Coarse license classification used by the policy decision.
 *
 * - [Permissive]: licenses that allow commercial use (Apache-2.0, MIT, ...).
 * - [NonCommercial]: licenses that forbid commercial use (CC-BY-NC, and models derived from the
 *   WLASL dataset, which carry the C-UDA non-commercial terms).
 * - [UserProvided]: a model supplied by the host with a host-declared license.
 * - [UnknownLicense]: the license could not be determined (e.g. a bring-your-own model without a
 *   valid signed manifest).
 */
enum class LicenseKind {
    /** Commercial use allowed. */
    Permissive,

    /** Commercial use forbidden. */
    NonCommercial,

    /** Host-supplied model with a declared license. */
    UserProvided,

    /** License not determinable. */
    UnknownLicense
}
