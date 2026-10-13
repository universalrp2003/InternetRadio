package com.universalrp.cleansweep.updates

import com.universalrp.updates.ReleaseRepositorySources
import org.junit.Assert.*
import org.junit.Test

/** Source-only prepared cases; must run in both authorized build variants before publication. */
class UpdatePolicyTest {
    @Test fun numericNewer() { assertTrue(UpdatePolicy.newer("2.38", "2.37")) }
    @Test fun equalIsNotNewer() { assertFalse(UpdatePolicy.newer("2.37", "2.37")) }
    @Test fun olderIsNotNewer() { assertFalse(UpdatePolicy.newer("2.36", "2.37")) }
    @Test fun paddedEqualIsNotNewer() { assertFalse(UpdatePolicy.newer("2.37.0", "2.37")) }
    @Test fun prereleaseIsNotStable() { assertFalse(UpdatePolicy.newer("2.38-beta", "2.37")) }
    @Test fun invalidVersionRejected() { assertFalse(UpdatePolicy.newer("invalid", "2.37")); assertFalse(UpdatePolicy.newer("99999999999999999999.1", "2.37")) }
    @Test fun officialAssetAllowedFromBothMigrationRepositories() {
        assertTrue(UpdatePolicy.officialAsset("https://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk"))
        assertTrue(UpdatePolicy.officialAsset("https://github.com/universalrp2003/RP-Android-Apps/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk"))
    }
    @Test fun releaseSourcesPreferDestinationButKeepOldFallback() {
        assertEquals(listOf("universalrp2003/RP-Android-Apps", "universalrp2003/InternetRadio"), ReleaseRepositorySources.ordered.map { it.slug })
        assertEquals(100, ReleaseRepositorySources.PAGE_SIZE)
        assertEquals(20, ReleaseRepositorySources.MAX_RELEASE_PAGES)
        assertEquals("https://api.github.com/repos/universalrp2003/RP-Android-Apps/releases?per_page=100&page=1", ReleaseRepositorySources.ordered.first().releasesPage(1))
        assertEquals("https://api.github.com/repos/universalrp2003/RP-Android-Apps/releases?per_page=100&page=20", ReleaseRepositorySources.ordered.first().releasesPage(ReleaseRepositorySources.MAX_RELEASE_PAGES))
        assertEquals("https://api.github.com/repos/universalrp2003/InternetRadio/releases?per_page=100&page=20", ReleaseRepositorySources.ordered.last().releasesPage(ReleaseRepositorySources.MAX_RELEASE_PAGES))
    }
    @Test fun canonicalReleaseAndAssetLinksMustMatchTheExactRepoTagAndName() {
        val destination = ReleaseRepositorySources.ordered.first()
        val archive = ReleaseRepositorySources.ordered.last()
        assertTrue(ReleaseRepositorySources.isOfficialReleasePage("https://github.com/universalrp2003/RP-Android-Apps/releases/tag/v-test", destination, "v-test"))
        assertTrue(ReleaseRepositorySources.isOfficialReleasePage("https://github.com/universalrp2003/InternetRadio/releases/tag/v-test", archive, "v-test"))
        assertFalse(ReleaseRepositorySources.isOfficialReleasePage("https://github.com/universalrp2003/RP-Android-Apps/releases/tag/other", destination, "v-test"))
        assertFalse(ReleaseRepositorySources.isOfficialReleasePage("https://github.com/universalrp2003/RP-Android-Apps/releases/tag/v-test?mirror=1", destination, "v-test"))
        assertTrue(UpdatePolicy.officialAsset("https://github.com/universalrp2003/RP-Android-Apps/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk", "v-test", destination))
        assertTrue(UpdatePolicy.officialAsset("https://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk", "v-test", archive))
        assertFalse(UpdatePolicy.officialAsset("https://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk", "v-test", destination))
        assertFalse(UpdatePolicy.officialAsset("https://github.com/universalrp2003/RP-Android-Apps/releases/download/other/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk", "v-test", destination))
        assertFalse(UpdatePolicy.officialAsset("https://github.com/universalrp2003/RP-Android-Apps/releases/download/v-test/CleanSweep-v2.38.apk?mirror=1", "CleanSweep-v2.38.apk", "v-test", destination))
    }
    @Test fun lookalikeHostRejected() { assertFalse(UpdatePolicy.officialAsset("https://github.com.evil.example/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun foreignRepoRejected() { assertFalse(UpdatePolicy.officialAsset("https://github.com/other/repo/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun insecureOrCredentialUrlRejected() { assertFalse(UpdatePolicy.officialAsset("http://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")); assertFalse(UpdatePolicy.officialAsset("https://name@github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun hashAndSizeAreRequired() { assertNull(UpdatePolicy.digest(null)); assertNull(UpdatePolicy.digest("sha256:bad")); assertEquals("a".repeat(64), UpdatePolicy.digest("sha256:" + "a".repeat(64))); assertFalse(UpdatePolicy.validSize(0)); assertFalse(UpdatePolicy.validSize(UpdatePolicy.MAX_APK_BYTES + 1)) }
    @Test fun onlySameStableSignerAllowed() { val stable = setOf(UpdatePolicy.SIGNER_SHA256); assertTrue(UpdatePolicy.trustedSigner(stable, stable)); assertFalse(UpdatePolicy.trustedSigner(stable, setOf("other"))); assertFalse(UpdatePolicy.trustedSigner(stable + "other", stable)); assertFalse(UpdatePolicy.trustedSigner(emptySet(), emptySet())) }
}
