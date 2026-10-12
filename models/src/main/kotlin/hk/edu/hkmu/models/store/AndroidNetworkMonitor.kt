package hk.edu.hkmu.models.store

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Production [NetworkMonitor] backed by [ConnectivityManager].
 *
 * Reports [NetworkState.Offline] when there is no active network (or it lacks internet), otherwise
 * [NetworkState.Unmetered] when the network declares `NOT_METERED`, and [NetworkState.Metered]
 * otherwise.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    override fun state(): NetworkState {
        val capabilities = connectivityManager?.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        return when {
            capabilities == null -> NetworkState.Offline
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.Offline
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkState.Unmetered
            else -> NetworkState.Metered
        }
    }
}
