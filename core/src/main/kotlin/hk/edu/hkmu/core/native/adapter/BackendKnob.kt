package hk.edu.hkmu.core.native.adapter

import hk.edu.hkmu.core.config.Accelerator

/**
 * A concrete, engine-specific backend knob that a selected [Accelerator] maps to.
 *
 * This is the only artifact an engine module needs from the adapter contract: it translates the
 * [token] (or the concrete knob value) into its own native configuration exactly once. Knobs are
 * **advisory tiers**, never a promise about a specific silicon block: a non-CPU knob always has a
 * CPU sibling, and the [BackendInitializer] drops to that sibling when the non-CPU knob fails to
 * initialize. v1 makes no NPU promise.
 */
interface BackendKnob {
    /** The accelerator tier this knob realizes. */
    val accelerator: Accelerator

    /** The engine-native token this knob maps to (e.g. an execution-provider id). */
    val token: String

    /** Stable diagnostic id, e.g. `sherpa.provider.cpu`. */
    val id: String
}
