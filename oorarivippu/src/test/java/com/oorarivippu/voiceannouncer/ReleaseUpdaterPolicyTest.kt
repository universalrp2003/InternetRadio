package com.oorarivippu.voiceannouncer

import com.universalrp.updates.AppUpdates
import com.universalrp.updates.ReleaseRepositorySources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdaterPolicyTest {
    @Test
    fun versionComparisonUsesNumericStableVersionsAndRejectsTestNames() {
        assertTrue(AppUpdates.newer("1.0.0", "0.9.9"))
        assertTrue(AppUpdates.newer("1.0.1", "1.0.0"))
        assertFalse(AppUpdates.newer("1.0.0", "1.0.0"))
        assertFalse(AppUpdates.newer("1.0.0", "0.1.0-test"))
        assertFalse(AppUpdates.newer("1.0.0-preview", "0.1.0"))
    }

    @Test
    fun bothOfficialRepositoriesAreUsedInDestinationFirstOrder() {
        assertEquals(
            listOf("universalrp2003/RP-Android-Apps", "universalrp2003/InternetRadio"),
            ReleaseRepositorySources.ordered.map { it.slug }
        )
    }

    @Test
    fun canonicalOorReleaseAndAssetUrlsAreRequired() {
        val source = ReleaseRepositorySources.bySlug("universalrp2003/RP-Android-Apps")!!
        val tag = "oor-arivippu-v1.0.0"
        val asset = "Oor-Arivippu-v1.0.0.apk"
        assertTrue(
            ReleaseRepositorySources.isOfficialReleasePage(
                "https://github.com/universalrp2003/RP-Android-Apps/releases/tag/$tag",
                source,
                tag
            )
        )
        assertTrue(
            ReleaseRepositorySources.isOfficialAsset(
                "https://github.com/universalrp2003/RP-Android-Apps/releases/download/$tag/$asset",
                source,
                tag,
                asset
            )
        )
        assertFalse(
            ReleaseRepositorySources.isOfficialAsset(
                "https://github.com.evil.example/universalrp2003/RP-Android-Apps/releases/download/$tag/$asset",
                source,
                tag,
                asset
            )
        )
    }
}
