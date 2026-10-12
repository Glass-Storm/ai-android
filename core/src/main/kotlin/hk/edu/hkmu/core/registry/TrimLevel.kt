package hk.edu.hkmu.core.registry

/**
 * Memory-pressure level the **caller** forwards into [ModelRegistry.onTrimMemory].
 *
 * The library owns no Android lifecycle (there is no Service/Activity/Application), so it cannot
 * receive trim callbacks itself. A host that does own a lifecycle forwards
 * `ComponentCallbacks2.onTrimMemory(level)` by mapping the raw integer through
 * [fromComponentCallbacks] and calling [ModelRegistry.onTrimMemory]. Android framework types are
 * deliberately kept out of this public API.
 *
 * Only idle (unpinned) models are ever unloaded; a model held through an active
 * [ModelLease] survives every level.
 */
enum class TrimLevel {
    /** `TRIM_MEMORY_RUNNING_MODERATE`: not actionable, keep every resident model warm. */
    RunningModerate,

    /** `TRIM_MEMORY_RUNNING_LOW`: drop idle models, keeping the single most-recently-used idle one. */
    RunningLow,

    /** `TRIM_MEMORY_RUNNING_CRITICAL`: drop every idle model. */
    RunningCritical,

    /** `TRIM_MEMORY_BACKGROUND`: drop idle models, keeping the single most-recently-used idle one. */
    Background,

    /** `TRIM_MEMORY_UI_HIDDEN`: no memory pressure, keep every resident model warm. */
    UiHidden,

    /** `TRIM_MEMORY_COMPLETE`: drop every idle model. */
    Complete;

    companion object {
        private const val TRIM_MEMORY_RUNNING_MODERATE = 5
        private const val TRIM_MEMORY_RUNNING_LOW = 10
        private const val TRIM_MEMORY_RUNNING_CRITICAL = 15
        private const val TRIM_MEMORY_UI_HIDDEN = 20
        private const val TRIM_MEMORY_BACKGROUND = 40
        private const val TRIM_MEMORY_MODERATE = 60
        private const val TRIM_MEMORY_COMPLETE = 80

        /**
         * Maps a raw `ComponentCallbacks2` trim level to a [TrimLevel].
         *
         * Unknown values are treated as [RunningModerate] (a no-op) so a future framework level can
         * never cause an unexpected unload.
         */
        fun fromComponentCallbacks(level: Int): TrimLevel = when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> RunningModerate
            TRIM_MEMORY_RUNNING_LOW -> RunningLow
            TRIM_MEMORY_RUNNING_CRITICAL -> RunningCritical
            TRIM_MEMORY_UI_HIDDEN -> UiHidden
            TRIM_MEMORY_BACKGROUND -> Background
            TRIM_MEMORY_MODERATE -> Background
            TRIM_MEMORY_COMPLETE -> Complete
            else -> RunningModerate
        }
    }
}
