package com.navigator.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            // Track which networks are currently validated; online = any of them is.
            private val validated = HashSet<Network>()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasRealInternet()) validated += network else validated -= network
                publish()
            }

            override fun onLost(network: Network) {
                validated -= network
                publish()
            }

            override fun onUnavailable() = publish()

            private fun publish() {
                val online = validated.isNotEmpty()
                if (_isOnline.value != online) {
                    _isOnline.value = online
                    AppLogger.log("Net", "connectivity -> ${if (online) "online" else "offline"}")
                }
            }
        }

        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure { AppLogger.log("Net", "!! registerNetworkCallback failed: ${it.message}") }
    }

    private fun NetworkCapabilities?.hasRealInternet(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
