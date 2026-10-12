package com.universalrp.cleansweep.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.updates.LiveGuardUpdates
import com.universalrp.cleansweep.voice.Announcer
import kotlinx.coroutines.flow.first

object FeatureSetupPrefs {
    fun prefs(context: Context) = context.applicationContext.getSharedPreferences("liveguard_reviewed_setup", Context.MODE_PRIVATE)
    fun reviewed(context: Context) = prefs(context).getBoolean("reviewed", false)
    fun reminders(context: Context) = prefs(context).getBoolean("notification_reminders", false)
    fun skipped(context: Context): Set<String> = prefs(context).getStringSet("skipped_reminders", emptySet()).orEmpty().toSet()
    fun skip(context: Context, id: String, value: Boolean) {
        val updated = if (value) skipped(context) + id else skipped(context) - id
        prefs(context).edit().putStringSet("skipped_reminders", updated).apply()
    }
    fun save(context: Context, reminders: Boolean) { prefs(context).edit().putBoolean("reviewed", true).putBoolean("notification_reminders", reminders).apply() }
}

object FeatureDirectory {
    fun storagePermission(context: Context): Boolean = if (android.os.Build.VERSION.SDK_INT >= 30) hasAllFilesAccess() else
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun onWifi(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        manager?.let { it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } == true
    }.getOrDefault(false)
    fun notificationPermission(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun list(context: Context, state: UiState): List<FeatureSetupPolicy.Feature> {
        val notifications = notificationPermission(context)
        val features = listOf(
            FeatureSetupPolicy.Feature("health", "Phone health", FeatureSetupPolicy.Status.READY, "Local battery and phone readings need no special access. Missing sensor values remain unavailable."),
            FeatureSetupPolicy.Feature("security", "Security check", FeatureSetupPolicy.Status.READY, "System apps are included by default, in the review scope and in the file-hash check, for inspection only. No permission is granted and no app is removed automatically. An unchecked or unknown hash proves nothing."),
            FeatureSetupPolicy.Feature("files", "File cleanup", FeatureSetupPolicy.status(true, access = state.hasAllFilesAccess), "File cleanup requires Android storage access. You review and select items; nothing is deleted by setup."),
            FeatureSetupPolicy.Feature("cache", "App cache cleaner", FeatureSetupPolicy.status(true, access = state.usageAccess), "Usage access is needed for cache sizes. The list refreshes when opened; system apps are visible by default but not auto-selected."),
            FeatureSetupPolicy.Feature("daily", "Daily local check", FeatureSetupPolicy.status(state.dailyScanOn, access = notifications), "Daily local checks and notifications follow your switches. Online daily summaries require a separate explicit choice."),
            FeatureSetupPolicy.Feature("background", "Persistent monitoring", FeatureSetupPolicy.status(state.persistentMonitor, access = notifications), "On by default since 2.42 and still opt-out: an ongoing notification with a Stop control. Turning it off is your choice, not a failure. Android force-stop and OEM restrictions still apply."),
            FeatureSetupPolicy.Feature("voice", "Voice alerts and answer voices", FeatureSetupPolicy.status(state.voiceOn), "Enable voice explicitly and preview actual installed language voices. Quiet hours and media volume can suppress speech; callbacks do not prove you heard it."),
            FeatureSetupPolicy.Feature("pill", "Floating status pill", FeatureSetupPolicy.status(state.statusPill, access = state.statusPillAllowed), "Optional overlay access is required. The floating pill keeps its existing charging-watts behavior."),
            FeatureSetupPolicy.Feature("ai", "Online assistant", FeatureSetupPolicy.status(state.assistantOnline, configured = state.aiConfig.ready), "Optional provider setup sends only your approved questions and allowed report facts. An API key or model name is not proof a model ran."),
            FeatureSetupPolicy.Feature("data", "Data plan and compact widgets", FeatureSetupPolicy.status(true, configured = state.dataPackInfo?.isUnlimited5g == true || onWifi(context) || DataUsageTracker.hasConfiguredPlan(context)), "Choose battery, data or phone widgets. Metered plans show available usage; Wi-Fi or user-set unlimited 5G shows useful phone details instead."),
            FeatureSetupPolicy.Feature("update", "In-app APK updates", FeatureSetupPolicy.status(true, access = LiveGuardUpdates.state.value.release == null || LiveGuardUpdates.canInstall(context)), "Checks only official published releases. Download verification and Android installer confirmation are mandatory; Play Protect cannot be forced or bypassed."),
        )
        val skipped = FeatureSetupPrefs.skipped(context)
        return features.map { if (it.id in skipped && it.status != FeatureSetupPolicy.Status.READY) it.copy(status = FeatureSetupPolicy.Status.OFF_BY_CHOICE) else it }
    }
    /** Existing worker only; this does not start a new permanent monitoring service. */
    suspend fun localState(context: Context): UiState {
        val settings = SettingsRepo(context)
        return UiState(hasAllFilesAccess = storagePermission(context), usageAccess = AppCacheRepo.hasUsageAccess(context),
            persistentMonitor = settings.persistentMonitor.first(), assistantOnline = settings.assistantOnline.first(),
            statusPill = settings.statusPill.first(), statusPillAllowed = com.universalrp.cleansweep.notify.StatusPill.canDraw(context),
            voiceOn = Announcer.enabled(context), dailyScanOn = Announcer.dailyScanOn(context),
            aiConfig = com.universalrp.cleansweep.ai.AiSettings.load(context), dataBudgetConfig = DataBudgetRepo.config(context),
            dataPackInfo = runCatching { DataUsageTracker.getUsageInfo(context) }.getOrNull())
    }
}
