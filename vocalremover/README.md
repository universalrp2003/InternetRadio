# RP Vocal Remover Studio

RP Vocal Remover Studio is a local-first Android vocal-reduction tool. Its latest published direct-download release is **0.1.10 / code 11**, package `com.universalrp.vocalremover`, minimum Android 10 (API 29). The app is distributed as a standalone APK from GitHub Releases; it is not included in the store-art pipeline.

- [Download RP-Vocal-Remover-Studio-v0.1.10.apk](https://github.com/universalrp2003/InternetRadio/releases/download/rp-vocal-remover-studio-v0.1.10/RP-Vocal-Remover-Studio-v0.1.10.apk) · [release notes](../releases/rp-vocal-remover-studio-v0.1.10.md)
- **Candidate 0.1.11 / code 12:** the CI build, reliability/regression and APK checks passed in [run 38513305483](https://github.com/universalrp2003/InternetRadio/actions/runs/38513305483), but release publication stopped before draft creation because `RP_ANDROID_APPS_RELEASE_TOKEN` is not configured. It remains standalone and has no public candidate APK available to download; its CI build hashes are in [`../docs/candidate-build-digests.md`](../docs/candidate-build-digests.md).
- [Release-asset size and SHA-256](../docs/published-release-digests.md)
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

The published updater checks RP-Android-Apps first and InternetRadio as fallback, then verifies the APK, package, version and signing identity. The unpublished 0.1.11 candidate adds the source display. The GitHub Actions workflow checks the merged manifest, native runtime assets and release digest before publication. There is no Android App Bundle target for this app in the current publishing pipeline.
