package com.oorarivippu.voiceannouncer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

/**
 * Listens for live changes to the phone's GPS status (LocationManager.PROVIDERS_CHANGED_ACTION).
 * When the user turns GPS OFF, callbacks immediately silence announcements and update UI status.
 */
class GpsStateReceiver(
    private val onGpsStateChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION ||
            intent?.action == LocationManager.MODE_CHANGED_ACTION
        ) {
            val isEnabled = isGpsProviderEnabled(context)
            onGpsStateChanged(isEnabled)
        }
    }

    companion object {
        fun isGpsProviderEnabled(context: Context): Boolean {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return false
            return try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                false
            }
        }
    }
}
