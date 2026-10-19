package com.oorarivippu.voiceannouncer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * Reports Android's validated default-network state. This is a practical internet
 * connectivity signal, not a guarantee that a particular geocoder backend is healthy.
 */
internal class InternetConnectivityMonitor(
    context: Context,
    private val onAvailabilityChanged: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshFromDefaultNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            publish(isValidatedInternet(capabilities))
        }

        override fun onLost(network: Network) {
            // Give Android a moment to select another default route before reporting offline.
            mainHandler.postDelayed({ refreshFromDefaultNetwork() }, 400L)
        }
    }

    fun start() {
        refreshFromDefaultNetwork()
        val manager = connectivityManager ?: return
        if (registered) return
        try {
            manager.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (_: Exception) {
            // Keep the one-time connectivity result if the OEM blocks callback registration.
        }
    }

    fun stop() {
        if (!registered) return
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // The callback may already have been removed by the system.
        }
        registered = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun refreshFromDefaultNetwork() {
        val manager = connectivityManager
        val network = manager?.activeNetwork
        val capabilities = if (network == null) null else manager.getNetworkCapabilities(network)
        publish(capabilities?.let(::isValidatedInternet) == true)
    }

    private fun publish(available: Boolean) {
        onAvailabilityChanged(available)
    }

    private fun isValidatedInternet(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
