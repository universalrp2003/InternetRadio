package com.universalrp.cleansweep.data

import android.content.Context
import android.os.SystemClock

/** One explicitly observed power-disconnect event, not a charging-history session or a guessed transition time. */
data class LastUnplug(val atMs: Long, val percent: Int?)
object LastUnplugPolicy {
    fun observation(atMs: Long, percent: Int?, explicitDisconnect: Boolean): LastUnplug? =
        if (explicitDisconnect && atMs > 0) LastUnplug(atMs, percent?.takeIf { it in 0..100 }) else null
    fun usable(value: LastUnplug?, now: Long): Boolean = value != null && value.atMs in 1..now
}
object LastUnplugRepo {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("liveguard_last_unplug", Context.MODE_PRIVATE)
    @Synchronized fun connected(context: Context) { prefs(context).edit().putBoolean("disconnect_seen", false).apply() }
    @Synchronized fun observe(context: Context, battery: BatteryReading, explicitDisconnect: Boolean) {
        if (!explicitDisconnect) return // A protected disconnect event outranks a delayed sticky battery flag.
        val value = LastUnplugPolicy.observation(System.currentTimeMillis(), BatteryValuePolicy.exactWholePercent(battery.percent, battery.reportedLevel, battery.reportedScale), true) ?: return
        val p = prefs(context)
        val elapsed = SystemClock.elapsedRealtime()
        // Runtime and manifest receivers may observe the same event. Keep its first genuine timestamp.
        val oldElapsed = p.getLong("elapsed", -30_000)
        if (p.getBoolean("disconnect_seen", false) && elapsed >= oldElapsed && elapsed - oldElapsed < 3_000) return
        p.edit().putLong("at", value.atMs).putInt("percent", value.percent ?: -1).putLong("elapsed", elapsed).putBoolean("disconnect_seen", true).apply()
    }
    fun load(context: Context): LastUnplug? {
        val p = prefs(context); val at = p.getLong("at", 0)
        return if (at > 0) LastUnplug(at, p.getInt("percent", -1).takeIf { it in 0..100 }) else null
    }
    fun clear(context: Context) { prefs(context).edit().clear().apply() }
}
