package com.universalrp.cleansweep.data

/**
 * Pure rules for the 2.40 core privacy cleanup.
 *
 * Live Guard / CleanSweep 2.40 removes the notification reader from the product. A phone
 * that had used 2.39 still holds a few private leftovers; this policy names exactly the
 * three things that may be retired and nothing else. Every other saved choice — API keys,
 * app language, A/B answer voices, voice/monitor switches, own alert choices, data plan,
 * charging and security histories, permission-review records and other apps' data — is
 * deliberately out of scope.
 *
 * Keeping the rules here (no Android imports) is what lets the 6 cleanup cases run as plain
 * JVM tests. The old reader's pure policy lives in the test sources only.
 */
object CorePrivacyMigrationPolicy {

    /** Private marker file: written only after every cleanup step succeeded. */
    const val MARKER_PREFS = "liveguard_core_privacy"
    const val MARKER_KEY = "core_privacy_2_40_cleaned"

    /** The removed reader's stored app/sender choices. The whole file is retired. */
    const val READER_PREFS = "liveguard_notification_reader"

    /** Reviewed-setup memory: the removed "reader" reminder id is retired from the set. */
    const val SKIPPED_PREFS = "liveguard_reviewed_setup"
    const val SKIPPED_KEY = "skipped_reminders"
    const val READER_FEATURE_ID = "reader"

    /** Plain voice preferences: only the reader's own key is retired. */
    const val VOICE_PREFS = "cleansweep_state"
    const val VOICE_MESSAGES_KEY = "voice_messages"

    /** The reader's last delivery event, used only to decide whether its diagnostics are stale. */
    const val VOICE_LAST_EVENT_KEY = "voice_last_event"
    const val MESSAGES_EVENT = "MESSAGES"

    /** Removed only when the last recorded automatic event was the retired reader's. */
    val VOICE_DIAGNOSTIC_KEYS = listOf("voice_last_stage", VOICE_LAST_EVENT_KEY, "voice_last_attempt_ms")

    /** Idempotent: an existing marker means the cleanup is already done and is not repeated. */
    fun complete(existingMarker: Boolean): Boolean = existingMarker

    /** Only the reader's own preferences file may be deleted. */
    fun readerPrefsRetired(name: String): Boolean = name == READER_PREFS

    /** Only the reader's own voice switch key may be removed; real alert switches stay. */
    fun retiredVoiceKey(key: String?): Boolean = key == VOICE_MESSAGES_KEY

    /**
     * Old voice diagnostics are retired only when they describe the removed reader. A genuine
     * battery/charging/answer diagnostic is kept, so the remaining diagnostics stay truthful.
     */
    fun retireVoiceDiagnostics(lastEvent: String?): Boolean = lastEvent == MESSAGES_EVENT

    /** Removes the obsolete reader reminder id and keeps every other skipped-reminder choice. */
    fun skippedRemindersAfterRetirement(existing: Set<String>): Set<String> = existing - READER_FEATURE_ID

    /** The marker is written only when all four cleanup steps succeeded. */
    fun everythingSucceeded(
        readerPrefsCleared: Boolean,
        voiceKeyCleared: Boolean,
        diagnosticsCleared: Boolean,
        remindersCleared: Boolean,
    ): Boolean = readerPrefsCleared && voiceKeyCleared && diagnosticsCleared && remindersCleared
}
