# RP Vocal Remover Studio

RP Vocal Remover Studio is a local-first Android vocal-reduction tool. Its latest published direct-download release is **0.1.11 / code 12**, package `com.universalrp.vocalremover`, minimum Android 10 (API 29). The app is distributed as a standalone APK from GitHub Releases; it is not included in the store-art pipeline.

- [Download RP-Vocal-Remover-Studio-v0.1.11.apk from RP-Android-Apps](https://github.com/universalrp2003/RP-Android-Apps/releases/download/rp-vocal-remover-studio-v0.1.11/RP-Vocal-Remover-Studio-v0.1.11.apk) · [same release on the InternetRadio fallback](https://github.com/universalrp2003/InternetRadio/releases/download/rp-vocal-remover-studio-v0.1.11/RP-Vocal-Remover-Studio-v0.1.11.apk) · [release notes](../releases/rp-vocal-remover-studio-v0.1.11.md)
- Build, reliability/regression and APK checks passed in [publication run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254). Published asset size and SHA-256 are in [`../docs/published-release-digests.md`](../docs/published-release-digests.md).
- [Privacy policy](../PRIVACY.md)

## Audio workflow and privacy

1. Select a local audio file. The selected song stays on the device; there is no selected-audio upload path or external audio-processing service.
2. The first processing pass creates an instrumental WAV. It does not overwrite or alter the original file.
3. Listen to the result. A second vocal-reduction pass is optional and starts only when the user requests it.
4. Optionally export a separate 320 kb/s CBR MP3. MP3 is lossy; the WAV remains available.

The model is downloaded from the upstream GitHub release and verified against its pinned checksum. Model delivery over the internet is allowed; this does not send the selected audio off-device.

The main screen retains the Slow / Medium / Fast selector. Processing supports pause/resume and long-running background work through a foreground service and wake lock, with an optional keep-screen-awake setting. Low-RAM pauses can resume after recovery; user-, thermal-, storage- and Android-service-limit pauses require manual resumption. CPU temperature, RAM and load are reported only where available and are labelled when device/OEM information is missing. The app does not claim to change Android's battery-optimization exemption.

## Build

```sh
./gradlew :vocalremover:testDebugUnitTest :vocalremover:testReleaseUnitTest
./gradlew :vocalremover:assembleRelease
```

The updater checks RP-Android-Apps first and InternetRadio as fallback, then verifies the APK, package, version and signing identity before Android's installer opens. It also displays the source of an available release. The GitHub Actions workflow checks the merged manifest, native runtime assets and release digest before publication. There is no Android App Bundle target for this app in the current publishing pipeline. The owner considers existing phone testing sufficient for this release; this does not claim the exact CI-published APK bytes were installed or verified on a phone.
