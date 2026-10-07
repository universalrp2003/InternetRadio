package com.universalrp.cleansweep.ai
import com.universalrp.updates.AppUpdates
import org.junit.Assert.*
import org.junit.Test
class UpdateVersionTest {
    @Test fun numericVersions() {
        assertTrue(AppUpdates.newer("2.13", "2.9"))
        assertFalse(AppUpdates.newer("2.9", "2.13"))
        assertFalse(AppUpdates.newer("2.13", "2.13"))
        assertFalse(AppUpdates.newer("broken", "2.13"))
    }
}
