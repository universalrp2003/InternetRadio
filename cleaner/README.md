# Live Guard (CleanSweep)

Live Guard is an Android phone-care and device-monitoring app. Its latest published APK is **2.45 / code 50**, package `com.universalrp.cleansweep`, minimum Android 8.0 (API 26).

- [Download CleanSweep-v2.45.apk](https://github.com/universalrp2003/InternetRadio/releases/download/live-guard-v2.45/CleanSweep-v2.45.apk) · [release notes](../releases/live-guard-v2.45.md)
- **Candidate 2.46 / code 51:** build, regression, permission-delta and APK checks passed in [CI run 38513305483](https://github.com/universalrp2003/InternetRadio/actions/runs/38513305483), but the workflow stopped before creating any release draft because `RP_ANDROID_APPS_RELEASE_TOKEN` is not configured. It is not yet available for download; no APK digest or device test is claimed.
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md) · [installation advisory](../docs/live-guard-play-protect-install-block.md)

## What it does

Live Guard includes user-selected junk review and cleanup, device-health readings, permission/security review, network and data tools, optional notifications/widgets, and optional AI assistance. Measurements that Android or an OEM does not expose are labelled as unavailable or estimated rather than invented. Nothing is deleted without the user's selection and confirmation.

The app does not include notification/message reading, an Accessibility service, SMS/contact/call-log access, or device-admin capability. Optional histories and persistent monitoring remain under the user's control. Network services and data shared when optional features are used are described in the [privacy policy](../PRIVACY.md).

## Updates and safety

The bridge updater checks RP-Android-Apps first and keeps InternetRadio as a verified fallback. It validates a stable release's canonical URL, size, SHA-256, package, newer version and signing certificate before handing installation to Android. It does not install silently or bypass Play Protect. Android may require per-app “Install unknown apps” permission and always presents its installer confirmation.

The unpublished 2.46 candidate's permission-delta gate found no newly added dangerous permission relative to 2.44, so no physical-device evidence record was required. CI built and checked it, but publication was blocked before any draft or public APK was created. No phone test of those exact candidate bytes is claimed.

## Build and verify

```sh
./gradlew :cleaner:testDebugUnitTest :cleaner:testReleaseUnitTest
./gradlew :cleaner:assembleRelease
```

The full CI workflow also runs the required CleanSweep regression suites, source/localization checks, merged-manifest and DEX gates, package/version/minimum-SDK checks, and stable-signature verification.
