# Ramesh Radio

Ramesh Radio is a Tamil-first radio and local-audio player with an in-app English option. Its current stable release is **1.9 / code 9**, package `com.universalrp.tamilnadufm`, minimum Android 8.0 (API 26).

- [Download Ramesh-Radio-v1.9.apk](https://github.com/universalrp2003/InternetRadio/releases/download/ramesh-radio-v1.9/Ramesh-Radio-v1.9.apk)
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
- [Privacy policy](../PRIVACY.md)

## Features

- Tamil and international station presets, with local favorites and search.
- The built-in station list is available offline; the public Radio-Browser directory is contacted only when the user starts a search.
- Play local audio files, with app-scoped equalization and playback controls.
- Stable GitHub release checks, with APK verification before installation is handed off to Android.

Radio streams require internet access and may be supplied by third-party stations. Local audio is played on-device and is not uploaded by the app. Android may require the per-app “Install unknown apps” permission for direct APK updates and shows an installer confirmation for every update.

## Build

```sh
./gradlew :radio:testDebugUnitTest :radio:testReleaseUnitTest
./gradlew :radio:assembleRelease
```

The release workflow also creates an Android App Bundle for store delivery. It verifies application identity, version, manifest, signing certificate and release asset digest.
