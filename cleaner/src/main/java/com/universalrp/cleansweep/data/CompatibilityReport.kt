package com.universalrp.cleansweep.data

/** Allowlisted diagnostic facts, never a dump of preferences, logs, network or AI data. */
enum class CheckStatus { READY, NEEDS_PERMISSION, DISABLED, NOT_REPORTED, DEVICE_TEST_NEEDED }

data class CompatibilityCheck(val id: String, val title: String, val status: CheckStatus, val detail: String)

data class CompatibilityReport(
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val checkedAtMs: Long,
    val checks: List<CompatibilityCheck>,
) {
    /** Intentionally exports ONLY these public device facts and known check IDs/statuses. */
    fun safeText(): String = buildString {
        appendLine("Live Guard compatibility self-test")
        appendLine("App: ${safe(appVersion)}")
        appendLine("Phone: ${safe(manufacturer)} ${safe(model)}")
        appendLine("Android: ${safe(androidVersion)} / API $sdk")
        appendLine("Checked at (epoch ms): $checkedAtMs")
        for (check in checks) {
            if (check.id !in EXPORTABLE_CHECKS) continue
            // Detail is deliberately NOT exported: do not turn this into a raw-log channel.
            appendLine("${check.id}: ${check.status.name}")
        }
        appendLine("Snapshot of permission/configuration/sensor availability only.")
        appendLine("Not certification of overlay rendering, OEM settings links or background survival.")
        appendLine("ROM version is not inferred; supply it manually if you want help.")
        append("No keys, installed-app names, messages, files, IP/SSID, serial or device IDs included.")
    }

    companion object {
        val EXPORTABLE_CHECKS = setOf(
            "overlay_permission", "pill_enabled", "notifications", "notification_channel",
            "background_monitor", "monitor_running", "battery_optimization", "background_restriction",
            "usage_access", "storage_access", "battery_level", "battery_current", "battery_voltage",
            "battery_temperature", "charging_power", "cpu_temperature", "memory_reading", "storage_reading", "settings_links", "overlay_rendering",
        )
        private fun safe(text: String): String = text.filter { !it.isISOControl() }.take(100)
    }
}
