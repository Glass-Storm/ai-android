package hk.edu.hkmu.models.store

/** Connectivity class relevant to a download decision. */
enum class NetworkState {
    /** No usable network; only pre-provisioned import can succeed. */
    Offline,

    /** A metered connection (mobile data, metered Wi-Fi). */
    Metered,

    /** An unmetered connection (unmetered Wi-Fi, ethernet). */
    Unmetered
}

/**
 * Supplies the current [NetworkState].
 *
 * The downloader depends on this interface rather than `ConnectivityManager` directly so the policy
 * is unit-testable on the JVM. Production hosts wire [AndroidNetworkMonitor]; tests inject a
 * constant-state fake.
 */
fun interface NetworkMonitor {
    /** @return the current connectivity class. */
    fun state(): NetworkState

    companion object {
        /** A monitor that always reports [NetworkState.Unmetered]. */
        val Unmetered: NetworkMonitor = NetworkMonitor { NetworkState.Unmetered }
    }
}

/** Policy for whether a download may use a [NetworkState.Metered] connection. */
enum class MeteredNetworkPolicy {
    /** Metered downloads are permitted. */
    AllowDownloads,

    /** Metered downloads are refused with [DownloadException.MeteredNetworkBlocked]. */
    BlockDownloads
}
