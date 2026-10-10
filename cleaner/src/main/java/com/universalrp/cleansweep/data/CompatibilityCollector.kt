package com.universalrp.cleansweep.data

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.universalrp.cleansweep.BuildConfig
import com.universalrp.cleansweep.notify.ChargeMonitorService
import com.universalrp.cleansweep.notify.ChargeNotifier
import com.universalrp.cleansweep.notify.StatusPill

/** Local reads only. Running this test never grants permissions, starts services or probes the network. */
object CompatibilityCollector {
    fun collect(context: Context): CompatibilityReport {
        val health = runCatching { DeviceHealthReader.read(context) }.getOrNull()
        val battery = health?.battery
        val checks = mutableListOf<CompatibilityCheck>()
        fun add(id: String, title: String, status: CheckStatus, detail: String) { checks += CompatibilityCheck(id, title, status, detail) }
        fun permission(id: String, title: String, allowed: Boolean?, detail: String) =
            add(id, title, when (allowed) { true -> CheckStatus.READY; false -> CheckStatus.NEEDS_PERMISSION; null -> CheckStatus.NOT_REPORTED }, detail)
        fun sensor(id: String, title: String, available: Boolean, detail: String) =
            add(id, title, if (available) CheckStatus.READY else CheckStatus.NOT_REPORTED, detail)
        val prefs = context.getSharedPreferences(ChargeMonitorService.PILL_PREFS, Context.MODE_PRIVATE)
        permission("overlay_permission", "Overlay permission", runCatching { android.provider.Settings.canDrawOverlays(context) }.getOrNull(), "Required only for the floating pill. Some OEMs can still limit status-bar placement.")
        add("pill_enabled", "Status pill switch", if (prefs.getBoolean(ChargeMonitorService.PILL_KEY, false)) CheckStatus.READY else CheckStatus.DISABLED, "Permission and the app switch are separate. The pill is still watts-only while connected.")
        val notifications = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrNull()
        permission("notifications", "Notifications", notifications, "On Android 13+, also check the notification permission. Notifications are not needed to read the local report.")
        val channel = runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.getNotificationChannel(ChargeNotifier.CHANNEL_ID) }.getOrNull()
        add("notification_channel", "Monitoring notification channel", when {
            channel == null -> CheckStatus.NOT_REPORTED
            channel.importance == NotificationManager.IMPORTANCE_NONE -> CheckStatus.DISABLED
            else -> CheckStatus.READY
        }, "The channel may not exist until monitoring first runs. A disabled channel can hide the card.")
        add("background_monitor", "Persistent monitoring", if (ChargeMonitorService.persistentEnabled(context)) CheckStatus.READY else CheckStatus.DISABLED, "Charging-only mode stops on unplug. This test does not enable persistent monitoring.")
        add("monitor_running", "Charging service running now", if (ChargeMonitorService.alive) CheckStatus.READY else CheckStatus.DISABLED, "This service in the current process only; fallback workers are separate. Not proof of reboot/autostart reliability.")
        val optimized = runCatching { !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName) }.getOrNull()
        add("battery_optimization", "Battery optimization", if (optimized == false) CheckStatus.READY else CheckStatus.DEVICE_TEST_NEEDED, "Optimization/autostart can affect background survival. Exemption is optional and does not guarantee OEM behaviour.")
        val restricted = if (Build.VERSION.SDK_INT >= 28) runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted }.getOrNull() else null
        add("background_restriction", "Android background restriction", when (restricted) { true -> CheckStatus.DISABLED; false -> CheckStatus.READY; null -> CheckStatus.NOT_REPORTED }, "API 28+ exposes this flag. OEM restrictions may exist independently.")
        permission("usage_access", "Usage access", AppInventoryLoader.hasUsageAccess(context, context.packageName), "Needed for full mobile history and app/cache statistics, not for battery readings.")
        val storage = runCatching { hasAllFilesAccess() && (Build.VERSION.SDK_INT >= 30 || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) }.getOrNull()
        permission("storage_access", "Storage access", storage, "Needed only for file scanning/cleaning; not required for charging or security history.")
        sensor("memory_reading", "RAM reading", health?.device?.totalRamBytes?.let { it > 0 } == true, "Unknown RAM is not zero percent; this is not a performance score.")
        sensor("storage_reading", "Storage reading", health?.storage?.total?.let { it > 0 } == true, "Capacity metadata only, not a scan of personal files.")
        sensor("battery_level", "Battery percentage", battery?.percent?.let { it in 0..100 } == true, "Reported by Android, not guessed.")
        sensor("battery_current", "Battery current sensor", battery?.currentA?.isFinite() == true, "Availability only; OEM units/calibration still need device validation.")
        sensor("battery_voltage", "Battery voltage", battery?.voltageV?.isFinite() == true, "Some ROMs do not expose this reading.")
        sensor("battery_temperature", "Battery temperature", battery?.temperatureC?.isFinite() == true, "This is the battery sensor, not CPU temperature.")
        sensor("charging_power", "Battery-side charging watts", battery?.powerW?.isFinite() == true, if (battery?.powerConnected == true) "Missing watts remain unknown, never charger-rated output." else "Connect a charger to test this reading. Unplugged does not mean the sensor is unsupported.")
        sensor("cpu_temperature", "CPU temperature", health?.cpuTempC?.isFinite() == true, "Kernel access varies. Missing readings remain not reported.")
        add("settings_links", "OEM settings shortcuts", CheckStatus.DEVICE_TEST_NEEDED, "Guarded routes/manual fallbacks exist. A permission snapshot cannot prove every OEM activity works.")
        add("overlay_rendering", "Overlay placement and background survival", CheckStatus.DEVICE_TEST_NEEDED, "Test plug/unplug, cutout/scale and reboot on this device. Permission granted does not prove rendering.")
        return CompatibilityReport(BuildConfig.VERSION_NAME, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT, System.currentTimeMillis(), checks)
    }
}
