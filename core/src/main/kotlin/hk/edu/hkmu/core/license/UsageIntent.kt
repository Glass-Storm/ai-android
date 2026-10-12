package hk.edu.hkmu.core.license

/**
 * How a host intends to use a model.
 *
 * - [SelfUse]: personal / non-commercial use. Permits non-commercial (NC) licenses with a recorded
 *   acknowledgment.
 * - [Commercial]: shipping inside a commercial product. Blocks NC licenses, dataset-derived NC
 *   models, and any model whose license cannot be determined.
 */
enum class UsageIntent {
    /** Personal / non-commercial use. */
    SelfUse,

    /** Use inside a commercial product. */
    Commercial
}
