package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Prepared declarations for the 2.40 core privacy cleanup. These are NOT executed tests until
 * both authorized build variants run them: 301 required cases, 33 suites.
 */
class CorePrivacyMigrationPolicyTest {

    @Test
    fun markerIsIdempotent() {
        assertTrue(CorePrivacyMigrationPolicy.complete(true))
        assertFalse(CorePrivacyMigrationPolicy.complete(false))
    }

    @Test
    fun onlyTheReaderPreferencesFileIsRetired() {
        assertTrue(CorePrivacyMigrationPolicy.readerPrefsRetired("liveguard_notification_reader"))
        assertFalse(CorePrivacyMigrationPolicy.readerPrefsRetired("cleansweep_state"))
        assertFalse(CorePrivacyMigrationPolicy.readerPrefsRetired("liveguard_reviewed_setup"))
    }

    @Test
    fun onlyTheReaderVoiceKeyIsRetired() {
        assertTrue(CorePrivacyMigrationPolicy.retiredVoiceKey("voice_messages"))
        assertFalse(CorePrivacyMigrationPolicy.retiredVoiceKey("voice_on"))
        assertFalse(CorePrivacyMigrationPolicy.retiredVoiceKey("voice_answers"))
        assertFalse(CorePrivacyMigrationPolicy.retiredVoiceKey(null))
    }

    @Test
    fun voiceDiagnosticsRetiredOnlyForTheReaderEvent() {
        assertTrue(CorePrivacyMigrationPolicy.retireVoiceDiagnostics("MESSAGES"))
        assertFalse(CorePrivacyMigrationPolicy.retireVoiceDiagnostics("BATTERY_LOW"))
        assertFalse(CorePrivacyMigrationPolicy.retireVoiceDiagnostics(""))
        assertFalse(CorePrivacyMigrationPolicy.retireVoiceDiagnostics(null))
    }

    @Test
    fun readerReminderRemovedAndOtherChoicesKept() {
        assertEquals(setOf("voice", "ai", "pill"), CorePrivacyMigrationPolicy.skippedRemindersAfterRetirement(setOf("reader", "voice", "ai", "pill")))
        assertEquals(emptySet<String>(), CorePrivacyMigrationPolicy.skippedRemindersAfterRetirement(setOf("reader")))
        assertEquals(setOf("voice"), CorePrivacyMigrationPolicy.skippedRemindersAfterRetirement(setOf("voice")))
    }

    @Test
    fun markerRequiresEveryCleanupStep() {
        assertTrue(CorePrivacyMigrationPolicy.everythingSucceeded(true, true, true, true))
        assertFalse(CorePrivacyMigrationPolicy.everythingSucceeded(false, true, true, true))
        assertFalse(CorePrivacyMigrationPolicy.everythingSucceeded(true, false, true, true))
        assertFalse(CorePrivacyMigrationPolicy.everythingSucceeded(true, true, false, true))
        assertFalse(CorePrivacyMigrationPolicy.everythingSucceeded(true, true, true, false))
    }
}
