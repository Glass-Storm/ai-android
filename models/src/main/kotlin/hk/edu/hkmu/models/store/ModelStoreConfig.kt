package hk.edu.hkmu.models.store

/**
 * Tunables for a [ModelStore].
 *
 * Defaults target production (a 30s idle read timeout, ~1 MiB progress granularity, a 32 MiB disk
 * safety margin). Tests override the timeouts and progress interval to keep fixtures small and
 * deterministic.
 */
data class ModelStoreConfig(
    val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    val progressIntervalBytes: Long = DEFAULT_PROGRESS_INTERVAL_BYTES,
    val diskSafetyMarginBytes: Long = DEFAULT_DISK_SAFETY_MARGIN_BYTES,
    val copyBufferBytes: Int = DEFAULT_COPY_BUFFER_BYTES
) {
    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
        const val DEFAULT_PROGRESS_INTERVAL_BYTES = 1L * 1024L * 1024L
        const val DEFAULT_DISK_SAFETY_MARGIN_BYTES = 32L * 1024L * 1024L
        const val DEFAULT_COPY_BUFFER_BYTES = 64 * 1024
    }
}
