package com.universalrp.cleansweep.notify

import java.util.Locale

/** A temporary display mode, never a change to the user's data/meter preferences. */
object StatusPillContent {
    data class Content(
        val text: String,
        val packPercent: Int?,
        val showNetworkMeter: Boolean,
    )

    fun resolve(
        powerConnected: Boolean,
        powerW: Float?,
        mobileData: String? = null,
        unlimited5g: Boolean = false,
        batteryPercent: Int = -1,
        packPercent: Int? = null,
        hideDataOnWifiOrUnlimited: Boolean = false,
    ): Content {
        if (powerConnected) {
            // A connected but paused/full charger still uses the watts-only mode. Never
            // substitute amps, battery %, a data counter, or a made-up zero for a missing sensor.
            val watts = powerW?.takeIf { it.isFinite() && it >= 0f }
            return Content(
                text = watts?.let { String.format(Locale.US, "%.1f W", it) } ?: "— W",
                packPercent = null,
                showNetworkMeter = false,
            )
        }
        val normalText = when {
            unlimited5g -> "5G Unlimited"
            mobileData != null -> mobileData
            batteryPercent >= 0 -> "$batteryPercent%"
            else -> "—"
        }
        return Content(
            text = if (hideDataOnWifiOrUnlimited) "" else normalText,
            packPercent = if (unlimited5g) null else packPercent?.coerceIn(0, 100),
            showNetworkMeter = true,
        )
    }
}
