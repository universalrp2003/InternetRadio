package com.universalrp.cleansweep.data

import android.content.Context
import java.io.File

/**
 * One-time, idempotent cleanup for the 2.40 core change that removed the notification reader.
 *
 * What it retires (and nothing else):
 *  * the reader's preferences file (stored app/sender choices),
 *  * the reader's own voice switch key,
 *  * the stored voice diagnostics **only** when their last event was the reader's,
 *  * the obsolete "reader" id in the skipped-setup reminders.
 *
 * It never logs a choice, never uploads anything, and never touches API keys, app language,
 * A/B answer voices, other voice or monitoring switches (including saved OFF choices), the
 * data plan, charging/security histories, genuine permission-review records, downloaded
 * public translation model files left behind in private app storage, or any other app's data.
 *
 * Runs from MainViewModel initialization on an IO dispatcher. If a step fails, no completion
 * marker is written and the whole cleanup is retried on a later start; a completed run writes
 * the marker once and is then a no-op.
 */
object CorePrivacyMigration {

    fun run(context: Context) {
        val app = context.applicationContext
        val marker = app.getSharedPreferences(CorePrivacyMigrationPolicy.MARKER_PREFS, Context.MODE_PRIVATE)
        if (CorePrivacyMigrationPolicy.complete(marker.getBoolean(CorePrivacyMigrationPolicy.MARKER_KEY, false))) return

        val readerPrefsCleared = clearReaderPreferences(app)
        val voiceKeyCleared = clearReaderVoiceKey(app)
        val diagnosticsCleared = clearReaderVoiceDiagnostics(app)
        val remindersCleared = clearReaderSetupReminder(app)

        if (CorePrivacyMigrationPolicy.everythingSucceeded(readerPrefsCleared, voiceKeyCleared, diagnosticsCleared, remindersCleared)) {
            marker.edit().putBoolean(CorePrivacyMigrationPolicy.MARKER_KEY, true).apply()
        }
    }

    /**
     * Deletes the reader's private file. Success means the file is gone afterwards, so a phone
     * that never had the reader (or a repeat run) also reports success instead of retrying forever.
     */
    private fun clearReaderPreferences(app: Context): Boolean = runCatching {
        val name = CorePrivacyMigrationPolicy.READER_PREFS
        val file = File(app.applicationInfo.dataDir, "shared_prefs" + File.separator + name + ".xml")
        if (CorePrivacyMigrationPolicy.readerPrefsRetired(name)) {
            app.deleteSharedPreferences(name)
            !file.exists()
        } else false
    }.getOrDefault(false)

    private fun clearReaderVoiceKey(app: Context): Boolean = runCatching {
        val key = CorePrivacyMigrationPolicy.VOICE_MESSAGES_KEY
        if (!CorePrivacyMigrationPolicy.retiredVoiceKey(key)) return@runCatching false
        val prefs = app.getSharedPreferences(CorePrivacyMigrationPolicy.VOICE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(key)) true
        else prefs.edit().remove(key).commit() && !prefs.contains(key)
    }.getOrDefault(false)

    private fun clearReaderVoiceDiagnostics(app: Context): Boolean = runCatching {
        val prefs = app.getSharedPreferences(CorePrivacyMigrationPolicy.VOICE_PREFS, Context.MODE_PRIVATE)
        val lastEvent = prefs.getString(CorePrivacyMigrationPolicy.VOICE_LAST_EVENT_KEY, null)
        if (!CorePrivacyMigrationPolicy.retireVoiceDiagnostics(lastEvent)) return@runCatching true
        val stored = CorePrivacyMigrationPolicy.VOICE_DIAGNOSTIC_KEYS.filter { prefs.contains(it) }
        if (stored.isEmpty()) true
        else {
            val edit = prefs.edit()
            stored.forEach { edit.remove(it) }
            edit.commit() && stored.none { prefs.contains(it) }
        }
    }.getOrDefault(false)

    private fun clearReaderSetupReminder(app: Context): Boolean = runCatching {
        val prefs = app.getSharedPreferences(CorePrivacyMigrationPolicy.SKIPPED_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(CorePrivacyMigrationPolicy.SKIPPED_KEY, emptySet()).orEmpty().toSet()
        val updated = CorePrivacyMigrationPolicy.skippedRemindersAfterRetirement(existing)
        if (updated == existing) true
        else prefs.edit().putStringSet(CorePrivacyMigrationPolicy.SKIPPED_KEY, updated).commit() &&
            prefs.getStringSet(CorePrivacyMigrationPolicy.SKIPPED_KEY, emptySet()).orEmpty().toSet() == updated
    }.getOrDefault(false)
}
