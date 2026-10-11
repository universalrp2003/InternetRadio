package com.universalrp.cleansweep.updates

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
    @Test fun officialAssetAllowed() { assertTrue(UpdatePolicy.officialAsset("https://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun lookalikeHostRejected() { assertFalse(UpdatePolicy.officialAsset("https://github.com.evil.example/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun foreignRepoRejected() { assertFalse(UpdatePolicy.officialAsset("https://github.com/other/repo/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun insecureOrCredentialUrlRejected() { assertFalse(UpdatePolicy.officialAsset("http://github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")); assertFalse(UpdatePolicy.officialAsset("https://name@github.com/universalrp2003/InternetRadio/releases/download/v-test/CleanSweep-v2.38.apk", "CleanSweep-v2.38.apk")) }
    @Test fun hashAndSizeAreRequired() { assertNull(UpdatePolicy.digest(null)); assertNull(UpdatePolicy.digest("sha256:bad")); assertEquals("a".repeat(64), UpdatePolicy.digest("sha256:" + "a".repeat(64))); assertFalse(UpdatePolicy.validSize(0)); assertFalse(UpdatePolicy.validSize(UpdatePolicy.MAX_APK_BYTES + 1)) }
    @Test fun onlySameStableSignerAllowed() { val stable = setOf(UpdatePolicy.SIGNER_SHA256); assertTrue(UpdatePolicy.trustedSigner(stable, stable)); assertFalse(UpdatePolicy.trustedSigner(stable, setOf("other"))); assertFalse(UpdatePolicy.trustedSigner(stable + "other", stable)); assertFalse(UpdatePolicy.trustedSigner(emptySet(), emptySet())) }
}
