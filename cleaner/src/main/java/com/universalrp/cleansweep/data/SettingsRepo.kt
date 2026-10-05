package com.universalrp.cleansweep.data

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val EXCLUDED = stringSetPreferencesKey("excluded_paths")
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

    val scanSettings: Flow<ScanSettings> = store.data.map { p ->
        ScanSettings(
            includeHidden = p[Keys.INCLUDE_HIDDEN] ?: false,
            dupMinBytes = (p[Keys.DUP_MIN_KB] ?: 100).toLong() * 1024L,
            largeThresholdBytes = (p[Keys.LARGE_MB] ?: 200).toLong() * 1024L * 1024L,
            oldDownloadDays = p[Keys.OLD_DAYS] ?: 30,
            apkOnlyInstalled = p[Keys.APK_INSTALLED_ONLY] ?: false,
            excludedPrefixes = (p[Keys.EXCLUDED] ?: emptySet()).mapNotNull { normalize(it) }.toSet(),
        )
    }

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
