# Live Guard (CleanSweep)

Live Guard is an Android phone-care and device-monitoring app. Its current stable APK is **2.44 / code 49**, package `com.universalrp.cleansweep`, minimum Android 8.0 (API 26).

- [Download CleanSweep-v2.44.apk](https://github.com/universalrp2003/InternetRadio/releases/download/live-guard-v2.44/CleanSweep-v2.44.apk) · [release notes](../releases/live-guard-v2.44.md)
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md) · [installation advisory](../docs/live-guard-play-protect-install-block.md)

## What it does

Live Guard includes user-selected junk review and cleanup, device-health readings, permission/security review, network and data tools, optional notifications/widgets, and optional AI assistance. Measurements that Android or an OEM does not expose are labelled as unavailable or estimated rather than invented. Nothing is deleted without the user's selection and confirmation.

The app does not include notification/message reading, an Accessibility service, SMS/contact/call-log access, or device-admin capability. Optional histories and persistent monitoring remain under the user's control. Network services and data shared when optional features are used are described in the [privacy policy](../PRIVACY.md).

## Updates and safety

Live Guard checks stable GitHub Releases, validates the APK's official URL, size, SHA-256, package, newer version and signing certificate, and then hands installation to Android. It does not install silently or bypass Play Protect. Android may require per-app “Install unknown apps” permission and always presents its installer confirmation.

The 2.44 permission-delta gate found no newly added dangerous permission relative to 2.43, so no physical-device evidence record was required for publication. The owner later reported on 13 October 2026 that all four current apps were installed and working on their phone. This is owner-reported device feedback, not an all-device compatibility claim.

## Build and verify

```sh
./gradlew :cleaner:testDebugUnitTest :cleaner:testReleaseUnitTest
./gradlew :cleaner:assembleRelease
```

The full CI workflow also runs the required CleanSweep regression suites, source/localization checks, merged-manifest and DEX gates, package/version/minimum-SDK checks, and stable-signature verification.
