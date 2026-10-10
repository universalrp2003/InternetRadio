package com.universalrp.cleansweep.data

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Persists scan options in a DataStore preferences file. */
class SettingsRepo(context: Context) {

    private val appContext = context.applicationContext

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    ) {
        appContext.filesDir.resolve("cleansweep_settings.preferences_pb")
    }

    private object Keys {
        val SOUNDS = booleanPreferencesKey("sounds_enabled")
        val ASSISTANT_VERBOSE = booleanPreferencesKey("assistant_verbose")
        val INCLUDE_HIDDEN = booleanPreferencesKey("include_hidden")
        val DUP_MIN_KB = intPreferencesKey("dup_min_kb")
        val LARGE_MB = intPreferencesKey("large_mb")
        val OLD_DAYS = intPreferencesKey("old_days")
        val APK_INSTALLED_ONLY = booleanPreferencesKey("apk_installed_only")
        val CHARGE_MONITOR = booleanPreferencesKey("charge_monitor")
        val ASSISTANT_ONLINE = booleanPreferencesKey("assistant_online")
        val EXCLUDED = stringSetPreferencesKey("excluded_paths")
        val DEFAULT_SELECTED = stringSetPreferencesKey("default_selected_kinds")
        val STATUS_PILL = booleanPreferencesKey("status_pill")
        val LANG = stringPreferencesKey("app_lang")
        val SPEED_MB = intPreferencesKey("speed_size_mb")
    }

    val soundsEnabled: Flow<Boolean> = store.data.map { p ->
        p[Keys.SOUNDS] ?: true
    }

    suspend fun setSoundsEnabled(value: Boolean) =
        store.edit { it[Keys.SOUNDS] = value }

    val assistantVerbose: Flow<Boolean> = store.data.map { p ->
        p[Keys.ASSISTANT_VERBOSE] ?: true
    }

    suspend fun setAssistantVerbose(value: Boolean) =
        store.edit { it[Keys.ASSISTANT_VERBOSE] = value }

    /** Ongoing charging notification in the status bar. */
    val chargeMonitor: Flow<Boolean> = store.data.map { p -> p[Keys.CHARGE_MONITOR] ?: true }

    suspend fun setChargeMonitor(value: Boolean) =
        store.edit { it[Keys.CHARGE_MONITOR] = value }

    /** true = the assistant answers through your AI provider, false = on-device engine. */
    val assistantOnline: Flow<Boolean> = store.data.map { p -> p[Keys.ASSISTANT_ONLINE] ?: false }

    suspend fun setAssistantOnline(value: Boolean) =
        store.edit { it[Keys.ASSISTANT_ONLINE] = value }

    val scanSettings: Flow<ScanSettings> = store.data.map { p ->
        ScanSettings(
            includeHidden = p[Keys.INCLUDE_HIDDEN] ?: false,
            dupMinBytes = (p[Keys.DUP_MIN_KB] ?: 100).toLong() * 1024L,
            largeThresholdBytes = (p[Keys.LARGE_MB] ?: 200).toLong() * 1024L * 1024L,
            oldDownloadDays = p[Keys.OLD_DAYS] ?: 30,
            apkOnlyInstalled = p[Keys.APK_INSTALLED_ONLY] ?: false,
            excludedPrefixes = (p[Keys.EXCLUDED] ?: emptySet()).mapNotNull { normalize(it) }.toSet(),
            defaultSelected = (p[Keys.DEFAULT_SELECTED] ?: emptySet())
                .mapNotNull { name -> JunkKind.entries.firstOrNull { it.name == name } }
                .toSet(),
        )
    }

    /**
     * Remembers which categories to tick automatically after a scan. CleanSweep ships with
     * none of them on, so a fresh scan shows everything unticked and you decide.
     */
    suspend fun setDefaultSelected(kind: JunkKind, on: Boolean) {
        store.edit { prefs ->
            val set = (prefs[Keys.DEFAULT_SELECTED] ?: emptySet()).toMutableSet()
            if (on) set.add(kind.name) else set.remove(kind.name)
            prefs[Keys.DEFAULT_SELECTED] = set
        }
    }

    /** Small "charging watts" pill drawn in the empty part of the status bar (off by default). */
    val statusPill: Flow<Boolean> = store.data.map { p -> p[Keys.STATUS_PILL] ?: false }

    suspend fun setStatusPill(value: Boolean) =
        store.edit { it[Keys.STATUS_PILL] = value }

    /** Menu language: "en" or "ta". */
    val lang: Flow<String> = store.data.map { p -> p[Keys.LANG] ?: "en" }

    suspend fun setLang(lang: AppLang) =
        store.edit { it[Keys.LANG] = lang.id }

    /**
     * The size the user picked for the last speed test, or 0 when they have never run one.
     * The screen asks for a size every time, and this only pre-selects their own last choice
     * instead of a size CleanSweep invented — a big test on a metered plan costs real money.
     */
    val speedSizeMb: Flow<Int> = store.data.map { p -> p[Keys.SPEED_MB] ?: 0 }

    suspend fun setSpeedSizeMb(mb: Int) =
        store.edit { it[Keys.SPEED_MB] = mb.coerceIn(0, 100) }

    suspend fun setIncludeHidden(value: Boolean) =
        store.edit { it[Keys.INCLUDE_HIDDEN] = value }

    suspend fun setDupMinKb(value: Int) =
        store.edit { it[Keys.DUP_MIN_KB] = value.coerceIn(50, 10240) }

    suspend fun setLargeMb(value: Int) =
        store.edit { it[Keys.LARGE_MB] = value.coerceIn(50, 2048) }

    suspend fun setOldDays(value: Int) =
        store.edit { it[Keys.OLD_DAYS] = value.coerceIn(7, 365) }

    suspend fun setApkInstalledOnly(value: Boolean) =
        store.edit { it[Keys.APK_INSTALLED_ONLY] = value }

    suspend fun addExclusion(rawPath: String) {
        val path = normalize(rawPath) ?: return
        store.edit { prefs ->
            val set = (prefs[Keys.EXCLUDED] ?: emptySet()).toMutableSet()
            set.add(path)
            prefs[Keys.EXCLUDED] = set
        }
    }

    suspend fun removeExclusion(path: String) {
        store.edit { prefs ->
            val set = (prefs[Keys.EXCLUDED] ?: emptySet()).toMutableSet()
            set.remove(path)
            prefs[Keys.EXCLUDED] = set
        }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (s.startsWith("~")) s = root + s.removePrefix("~")
        if (!s.startsWith("/")) s = root + File.separator + s
        while (s.endsWith("/") && s.length > 1) s = s.dropLast(1)
        return s
    }
}
