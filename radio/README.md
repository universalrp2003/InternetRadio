# Ramesh Radio

Ramesh Radio is a Tamil-first radio and local-audio player with an in-app English option. Its latest published release is **1.10 / code 10**, package `com.universalrp.tamilnadufm`, minimum Android 8.0 (API 26).

- [Download Ramesh-Radio-v1.10.apk](https://github.com/universalrp2003/InternetRadio/releases/download/ramesh-radio-v1.10/Ramesh-Radio-v1.10.apk) · [release notes](../releases/ramesh-radio-v1.10.md)
- **Candidate 1.11 / code 11:** the CI build, unit/regression and APK checks passed in [run 38513305483](https://github.com/universalrp2003/InternetRadio/actions/runs/38513305483), but release publication stopped before draft creation because `RP_ANDROID_APPS_RELEASE_TOKEN` is not configured. This candidate's boost fix, Tamil-first popularity ordering and update-source display are not yet available in a public APK.
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md)

## Features

- Tamil and international station presets, with local favorites and search.
- The bundled station list is available offline. The current published app ranks verified stations and uses Radio Browser community votes; vote counts are not listener counts. The directory is contacted only when the user starts a search. The unpublished 1.11 candidate additionally keeps verified streams ahead, prioritizes Tamil within each health group in mixed lists, and uses votes to rank popularity.
- Play local audio files with app-scoped equalization and playback controls. The published 1.10 includes the 0–10 dB session-boost control; candidate 1.11 corrects the Android 9+ effect path and retains a LoudnessEnhancer fallback.
- Stable APK updates are verified for URL, digest, package, version and signer before Android's installer opens. Candidate 1.11 adds the selected repository display and new-first `RP-Android-Apps` source while retaining the `InternetRadio` fallback.

Radio streams require internet access and may be supplied by third-party stations. Local audio is played on-device and is not uploaded by the app. Android may require the per-app “Install unknown apps” permission for direct APK updates and shows an installer confirmation for every update.

## Build

```sh
./gradlew :radio:testDebugUnitTest :radio:testReleaseUnitTest
./gradlew :radio:assembleRelease
```

The release workflow also creates an Android App Bundle for store delivery. It verifies application identity, version, manifest, signing certificate and release asset digest.
