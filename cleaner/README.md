# Live Guard (CleanSweep)

Live Guard is an Android phone-care and device-monitoring app. Its latest published APK is **2.46 / code 51**, package `com.universalrp.cleansweep`, minimum Android 8.0 (API 26).

- [Download CleanSweep-v2.46.apk from RP-Android-Apps](https://github.com/universalrp2003/RP-Android-Apps/releases/download/live-guard-v2.46/CleanSweep-v2.46.apk) · [same release on the InternetRadio fallback](https://github.com/universalrp2003/InternetRadio/releases/download/live-guard-v2.46/CleanSweep-v2.46.apk) · [release notes](../releases/live-guard-v2.46.md)
- Build, regression, permission-delta and APK checks passed in [publication run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254). Published asset size and SHA-256 are in [`../docs/published-release-digests.md`](../docs/published-release-digests.md); no exact-build phone test is claimed.
- [Privacy policy](../PRIVACY.md) · [installation advisory](../docs/live-guard-play-protect-install-block.md)

## What it does

Live Guard includes user-selected junk review and cleanup, device-health readings, permission/security review, network and data tools, optional notifications/widgets, and optional AI assistance. Measurements that Android or an OEM does not expose are labelled as unavailable or estimated rather than invented. Nothing is deleted without the user's selection and confirmation.

The app does not include notification/message reading, an Accessibility service, SMS/contact/call-log access, or device-admin capability. Optional histories and persistent monitoring remain under the user's control. Network services and data shared when optional features are used are described in the [privacy policy](../PRIVACY.md).

## Updates and safety

The updater checks RP-Android-Apps first and keeps InternetRadio as a verified fallback. It validates a stable release's canonical URL, size, SHA-256, package, newer version and signing certificate before handing installation to Android. It does not install silently or bypass Play Protect. Android may require per-app “Install unknown apps” permission and always presents its installer confirmation.

The 2.46 permission-delta gate passed without requiring a new physical-device evidence record. The public API/source-order check found the stable 2.46 release on RP-Android-Apps and verified that its asset digest matches the InternetRadio fallback. CI/API validation does not verify an on-device download or installation of these exact bytes.

## Build and verify

```sh
./gradlew :cleaner:testDebugUnitTest :cleaner:testReleaseUnitTest
./gradlew :cleaner:assembleRelease
```

The full CI workflow also runs the required CleanSweep regression suites, source/localization checks, merged-manifest and DEX gates, package/version/minimum-SDK checks, and stable-signature verification.
