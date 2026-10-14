# Ramesh Radio

Ramesh Radio is a Tamil-first radio and local-audio player with an in-app English option. Its current stable release is **1.11 / code 11**, package `com.universalrp.tamilnadufm`, minimum Android 8.0 (API 26).

- [Download Ramesh-Radio-v1.11.apk](https://github.com/universalrp2003/InternetRadio/releases/download/ramesh-radio-v1.11/Ramesh-Radio-v1.11.apk) · [release notes](../releases/ramesh-radio-v1.11.md)
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md)

## Features

- Tamil and international station presets, with local favorites and search.
- The bundled station list is available offline. Mixed lists keep health-verified streams ahead of unchecked entries, then prioritize Tamil within each health group; Radio Browser community votes order each group. Category lists also keep verified streams first and sort by votes within each health group. Vote counts are not listener counts. The directory is contacted only when the user starts a search.
- Play local audio files, with app-scoped equalization, a working 0–10 dB session boost, and playback controls.
- The updater checks RP-Android-Apps first and InternetRadio as fallback, displays the source of an available release, and verifies the URL, APK digest, package, version and signer before Android's installer is opened.

Radio streams require internet access and may be supplied by third-party stations. Local audio is played on-device and is not uploaded by the app. Android may require the per-app “Install unknown apps” permission for direct APK updates and shows an installer confirmation for every update.

## Build

```sh
./gradlew :radio:testDebugUnitTest :radio:testReleaseUnitTest
./gradlew :radio:assembleRelease
```

The release workflow also creates an Android App Bundle for store delivery. It verifies application identity, version, manifest, signing certificate and release asset digest.
