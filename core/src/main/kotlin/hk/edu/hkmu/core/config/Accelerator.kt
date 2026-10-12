package hk.edu.hkmu.core.config

/**
 * Hardware accelerator preference for an engine.
 *
 * [Auto] lets the capability probe (native loader) pick the best available backend and fall back
 * to [Cpu]. [Cpu] and [Gpu] are explicit requests: when an explicitly requested accelerator is not
 * available the loader must surface an `AcceleratorUnavailable` error instead of silently
 * downgrading, so callers are never surprised by a slower backend.
 */
enum class Accelerator {
    /** Best available backend, falling back to CPU. */
    Auto,

    /** Force the CPU backend. */
    Cpu,

    /** Force the GPU backend; unavailable GPU is an error, not a fallback. */
    Gpu
}
