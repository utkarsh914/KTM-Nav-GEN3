package com.navigator.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.navigator.app.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide internet reachability, exposed as a [StateFlow]. "Online" means
 * the default network has both `INTERNET` and `VALIDATED` capabilities, so a
 * captive-portal / dead Wi-Fi correctly reads as offline (which is what matters
 * for navigation: routing, rerouting, traffic and map tiles all need real data).
 *
 * Singleton so the UI, the dash pipeline and services can all observe the same
 * signal. [init] is called once from `OpenDashApplication`; before that (and if
 * registration ever fails) it defaults to `true` so nothing shows a false
 * "offline" state.
 */
object ConnectivityMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return

        // Seed synchronously so the first frame doesn't flash "offline".
        _isOnline.value = runCatching {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps.hasRealInternet()
        }.getOrDefault(true)

        // The DEFAULT network only: what the rider's traffic actually uses. (A generic
        // INTERNET request fires for every network, including ones that aren't in use.)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                publish(caps.hasRealInternet())

            override fun onLost(network: Network) = publish(false)

            override fun onUnavailable() = publish(false)

            private fun publish(online: Boolean) {
                if (_isOnline.value != online) {
                    _isOnline.value = online
                    AppLogger.log("Net", "connectivity -> ${if (online) "online" else "offline"}")
                }
            }
        }

        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { AppLogger.log("Net", "!! registerDefaultNetworkCallback failed: ${it.message}") }
    }

    private fun NetworkCapabilities?.hasRealInternet(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
