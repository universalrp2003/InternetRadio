package com.rpideas.secretmessage.preview.settings

import com.rpideas.secretmessage.preview.audio.BuiltInNotificationSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreviewSettingsTest {
    @Test
    fun defaultsUseSixtySecondTimerFirstToneAndNoLock() {
        val defaults = PreviewSettings()
        assertEquals(60, defaults.viewSeconds)
        assertEquals(BuiltInNotificationSound.CRYSTAL_PULSE, defaults.tone)
        assertFalse(defaults.appLockEnabled)
    }

    @Test
    fun sanitizedSettingsClampTheTimerAndFallBackToTheDefaultTone() {
        assertEquals(60, PreviewSettings(viewSeconds = 0).sanitized().viewSeconds)
        assertEquals(300, PreviewSettings(viewSeconds = 10_000).sanitized().viewSeconds)
        assertEquals(150, PreviewSettings(viewSeconds = 149).sanitized().viewSeconds)
        assertEquals(1, PreviewSettings(toneId = 42).sanitized().toneId)
        assertEquals(4, PreviewSettings(toneId = 4).sanitized().toneId)
    }
}
