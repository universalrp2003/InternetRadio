package com.universalrp.cleansweep.voice

import org.junit.Assert.*
import org.junit.Test

class AnswerVoicePolicyTest {
    @Test fun twoVoicesAlternateBetweenAnswers() { assertEquals(listOf(0, 1, 0, 1), (0L..3L).map { AnswerVoicePolicy.slot(it, AnswerVoicePolicy.Mode.ALTERNATE) }) }
    @Test fun aSingleAnswerHasOnlyOneAssignedSlot() { val index = 8L; repeat(5) { assertEquals(0, AnswerVoicePolicy.slot(index, AnswerVoicePolicy.Mode.ALTERNATE)) } }
    @Test fun confirmedChoiceIsDefault() { assertEquals(AnswerVoicePolicy.Mode.ALTERNATE, AnswerVoicePolicy.Mode.parse("alternate_answers")); assertEquals(AnswerVoicePolicy.Mode.ALTERNATE, AnswerVoicePolicy.Mode.parse(null)) }
    @Test fun invalidIndexCannotBecomeInvalidVoice() { assertEquals(0, AnswerVoicePolicy.slot(-10, AnswerVoicePolicy.Mode.ALTERNATE)) }
    @Test fun answersNeverRetryIntoAnotherVoice() { assertFalse(AnswerVoicePolicy.retryInAnotherVoice(false, true)); assertFalse(AnswerVoicePolicy.retryInAnotherVoice(true, true)) }
    @Test fun startedAlertCannotBeReplayedEither() { assertFalse(AnswerVoicePolicy.retryInAnotherVoice(true, false)); assertTrue(AnswerVoicePolicy.retryInAnotherVoice(false, false)) }
    @Test fun preferencesAreValidated() { assertNull(AnswerVoicePolicy.validPreference(" ")); assertNull(AnswerVoicePolicy.validPreference("a".repeat(201))); assertEquals("ta-in-voice-a", AnswerVoicePolicy.validPreference(" ta-in-voice-a ")) }
    @Test fun descriptorContainsCapabilitiesNotGuessedGender() { val voice = InstalledAnswerVoice("engine-provided-id", "ta-IN", false); assertEquals("ta-IN", voice.language); assertFalse(voice.toString().contains("female")); assertFalse(voice.toString().contains("male")) }
}
