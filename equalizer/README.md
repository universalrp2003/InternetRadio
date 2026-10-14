# PulseEQ

PulseEQ is a Tamil-first audio player and 20-band equalizer with an in-app English option. Its latest published release is **1.4 / code 5**, package `com.universalrp.pulseeq`, minimum Android 8.0 (API 26).

- [Download PulseEQ-v1.4.apk](https://github.com/universalrp2003/InternetRadio/releases/download/pulseeq-v1.4/PulseEQ-v1.4.apk) · [release notes](../releases/pulseeq-v1.4.md)
- **Candidate 1.5 / code 6:** the CI build, unit/regression and APK checks passed in [run 38513305483](https://github.com/universalrp2003/InternetRadio/actions/runs/38513305483), but release publication stopped before draft creation because `RP_ANDROID_APPS_RELEASE_TOKEN` is not configured. It is not yet available as a public APK; candidate build hashes are recorded in [`../docs/candidate-build-digests.md`](../docs/candidate-build-digests.md), and updater-source display/new-source routing remain unreleased.
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md)

## Features and audio privacy

- Twenty-band processing, saved presets, spectrum display and local audio playback.
- Playback and audio processing happen on the device. Whether an equalizer effect applies to audio outside this app depends on the Android version, audio session and device/ROM; system-wide operation is not guaranteed.
- The optional stable-release check contacts GitHub. Selected local audio is not uploaded by PulseEQ.

The updater checks RP-Android-Apps first and InternetRadio as fallback, displays the source of an available release, and validates APK updates before Android's package installer is opened. Android may require per-app “Install unknown apps” access and always presents its own confirmation.

## Build

```sh
./gradlew :equalizer:testDebugUnitTest :equalizer:testReleaseUnitTest
./gradlew :equalizer:assembleRelease
```

The release workflow also produces an Android App Bundle and verifies application identity, version, manifest, signing certificate and release asset digest.
