package com.universalrp.cleansweep.data

import org.junit.Assert.*
import org.junit.Test

class CompatibilityReportTest {
    private fun report(checks: List<CompatibilityCheck> = emptyList(), model: String = "Redmi 13 5G") =
        CompatibilityReport("2.35", "Xiaomi", model, "8", 26, 1000, checks)
    @Test fun unknownCheckIdsCannotExportArbitraryState() {
        val text = report(listOf(CompatibilityCheck("private_state", "api key", CheckStatus.READY, "secret"))).safeText()
        assertFalse(text.contains("private_state")); assertFalse(text.contains("secret")); assertTrue(text.contains("API 26"))
    }
    @Test fun knownChecksExportStatusButNeverRawDetailOrLabels() {
        val text = report(listOf(CompatibilityCheck("usage_access", "PRIVATE_APP_LABEL", CheckStatus.NEEDS_PERMISSION, "SSID secret-key org.private.bank"))).safeText()
        assertTrue(text.contains("usage_access: NEEDS_PERMISSION")); assertFalse(text.contains("PRIVATE_APP_LABEL"))
        assertFalse(text.contains("secret-key")); assertFalse(text.contains("org.private.bank"))
    }
    @Test fun publicFactsAreBoundedAndControlCharactersRemoved() {
        val text = report(model = "phone\nINJECT\t" + "x".repeat(500)).safeText()
        assertFalse(text.contains("phone\nINJECT")); assertFalse(text.contains("x".repeat(101)))
        assertTrue(text.contains("App: 2.35")); assertTrue(text.contains("Checked at (epoch ms): 1000"))
    }
    @Test fun diagnosticNeverClaimsOemCertificationOrKnownRomVersion() {
        val text = report().safeText()
        assertTrue(text.contains("Not certification")); assertTrue(text.contains("ROM version is not inferred"))
        assertTrue(text.contains("No keys")); assertTrue(text.contains("IP/SSID"))
    }
}
