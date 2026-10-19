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
        charging: Boolean = false,
        chargeStatusText: String? = null,
        estimatingText: String = "Estimating",
        unavailableText: String = "Unavailable",
    ): Content {
        if (powerConnected) {
            // A zero/near-zero or unsupported sensor is not a watt reading. Use only a real
            // battery-side measurement; otherwise show a measured-history ETA/status, never
            // an empty number, an amp-for-watt substitution, or a made-up 0 W.
            val watts = powerW?.takeIf { it.isFinite() && it > 0.05f }
            val display = watts?.let { String.format(Locale.US, "%.1f W", it) }
                ?: chargeStatusText?.takeIf { it.isNotBlank() }
                ?: if (charging) estimatingText else unavailableText
            return Content(
                text = display,
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
