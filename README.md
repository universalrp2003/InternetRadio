# RP Ideas — Android apps

Five actively maintained, open-source Android apps developed by **Ramesh prathap .R** with shared project contributions, including software assistance from **Arena.ai Agent Mode**. The apps offer Tamil-first interfaces with an in-app English option. Current minimum Android versions are listed below; device-dependent features are described honestly in each app's documentation.

| App | Module | Current stable release (RP-Android-Apps) | Android | Purpose |
|---|---|---:|---:|---|
| **Live Guard (CleanSweep)** | `cleaner` | [2.46](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46) | 8.0+ (API 26) | Phone cleanup, health and security review, network tools, and optional assistance. |
| **Ramesh Radio** | `radio` | [1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11) | 8.0+ (API 26) | Tamil and world radio, vote-ranked station lists, local playback, and session boost. |
| **PulseEQ** | `equalizer` | [1.5](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5) | 8.0+ (API 26) | Twenty-band audio DSP, presets, spectrum view, and local playback. |
| **RP Vocal Remover Studio** | `vocalremover` | [0.1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11) | 10+ (API 29) | Local vocal reduction with WAV output and optional MP3 export. |
| **Oor Arivippu** | `oorarivippu` | [1.0.2](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.0.2) | 8.0+ (API 26) | Tamil/English GPS place and destination announcer with configurable offline-status reminders. |

**Secret Message is in development and is not a released or secure messenger.** Its isolated `:secretmessage` preview (0.2.1-preview) shows the WhisperPad product screens on one phone only: built-in keyboard and a large drawing pad, timers, tones and an optional app lock. It has no network connection, pairing or cryptography, and messages are never delivered; each unsent message disappears after 30 seconds. Do not enter sensitive content. See [`docs/secret-message-security-design.md`](docs/secret-message-security-design.md).

## Stable releases

The existing four-app batch (Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio) passed build, unit/regression, permission, package, signature and release-asset checks in [GitHub Actions run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254). Oor Arivippu is published separately, outside that batch: its current release, 1.0.2 / code 4, was published by the standalone [release run 38840264469](https://github.com/universalrp2003/InternetRadio/actions/runs/38840264469) (1.0.1 / code 3 by [run 38826899278](https://github.com/universalrp2003/InternetRadio/actions/runs/38826899278) and 1.0.0 / code 2 by [run 38748688974](https://github.com/universalrp2003/InternetRadio/actions/runs/38748688974), both unchanged). Both repositories serve the same signed Oor 1.0.2 APK at canonical release and download URLs. Filenames, sizes and verified SHA-256 values for all five current releases are in [`docs/published-release-digests.md`](docs/published-release-digests.md); release pages are the authoritative download source.

The owner reports the stable Oor Arivippu 1.0.0 app is working and was tested on two devices; those screenshots show package `com.oorarivippu.voiceannouncer` version 1.0.0 (2), Play Protect's “This app looks safe” screen, and the running app. For 1.0.1, the owner tested the debug APK from [run 38776902130](https://github.com/universalrp2003/InternetRadio/actions/runs/38776902130), built from the same app source before the version was raised, and reported that it works, including the Tamil/English voice picker and place announcements; after publication, the owner also installed the debug APK of the exact 1.0.1 source from [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762), which reported 1.0.1 (3), and reported that it works. None of the screenshots shows an APK SHA-256, and debug test builds cannot share the stable APK's bytes, so no installed bytes are hash-matched to a public asset. 1.0.2, which shortens place announcements to the place names, was published at the owner's request without a device test. CI success and public-asset verification are not an all-device compatibility claim.

## Install and update

Download each signed APK from its linked GitHub Release page. The apps check for newer stable releases and verify the published APK metadata before offering an update. Downloading does not install silently: Android may require the per-app **Install unknown apps** permission and always shows its installer confirmation. Play Protect and Android's signature checks are not bypassed.

For download filenames, sizes and verified SHA-256 values, see [`docs/published-release-digests.md`](docs/published-release-digests.md).

> **Live Guard test note:** the 2.46 publication passed the permission-delta gate without requiring a new physical-device evidence record. CI and public-asset verification passed, but those checks are not an exact-build phone test or Play Protect approval. See the [installation advisory](docs/live-guard-play-protect-install-block.md) and [release notes](releases/live-guard-v2.46.md).

## Privacy and audio

- Live Guard stores its local settings and optional histories on-device. Optional AI, web, hash and network tools contact the selected third-party services when used; see [`PRIVACY.md`](PRIVACY.md) before enabling them.
- Ramesh Radio contacts the selected station to stream and searches the public station directory only when requested. Local songs stay on the device.
- PulseEQ processes audio locally. Its optional update check contacts GitHub; system-wide effects remain device/ROM-dependent.
- RP Vocal Remover Studio downloads its checksum-pinned model from the upstream GitHub release. Selected songs are processed on-device; the app has no selected-audio upload path. The first output is a WAV, the optional second pass is user-started, and MP3 export is lossy while the WAV is retained.
- Oor Arivippu uses location and Android's Geocoder for trip announcements and destination lookup. Settings and the selected destination are stored locally; the Android/OEM Geocoder provider may process lookups according to its own policy. See [`PRIVACY.md`](PRIVACY.md).

The developer does not operate an app backend, ad network or analytics service. Third-party services may receive requests when an online feature is used or enabled and during update checks, under their own policies; see the privacy policy for details.

## Build

The project uses JDK 17, Gradle 8.14.3, the Android SDK and NDK. Open the repository root in Android Studio or use the wrapper:

```sh
./gradlew :cleaner:testDebugUnitTest :cleaner:testReleaseUnitTest \
  :radio:testDebugUnitTest :radio:testReleaseUnitTest \
  :equalizer:testDebugUnitTest :equalizer:testReleaseUnitTest \
  :vocalremover:testDebugUnitTest :vocalremover:testReleaseUnitTest

./gradlew :cleaner:assembleRelease :radio:assembleRelease \
  :equalizer:assembleRelease :vocalremover:assembleRelease

./gradlew :oorarivippu:testReleaseUnitTest :oorarivippu:lintRelease :oorarivippu:assembleRelease
```

For the **non-shipping Secret Message preview only**, run `./gradlew :secretmessage:testDebugUnitTest :secretmessage:lintDebug :secretmessage:assembleDebug`; this produces a debug preview, not a release APK. GitHub Actions builds the four legacy batch apps and performs the required localization, regression, package/version/signature and release-asset checks. Oor Arivippu has its own test and marker-gated stable release workflows. The preview is excluded from every release and bundle task and has its own test-only workflow (`secret-message-preview.yml`): contract test, unit tests, lint, debug APK, a merged-permission allowlist, and a hosted API 35 emulator UI smoke test. All of these passed in [run 38835582621](https://github.com/universalrp2003/InternetRadio/actions/runs/38835582621); that is not a physical-device test. The four-app batch workflow generates Play bundles for CleanSweep, Ramesh Radio and PulseEQ; RP Vocal Remover Studio and Oor Arivippu are distributed as standalone APKs.

## Project scope and repository URLs

There are five active products: the four legacy batch apps listed above plus Oor Arivippu, which uses its own standalone stable release workflow. Secret Message remains a separate local-only development preview. Earlier app modules were retired from the active source tree; their already-published GitHub Releases and tags are retained as historical records and are not built by current CI.

`RP-Android-Apps` is the primary updater source and `InternetRadio` is the verified fallback. The four legacy bridge clients check the new repository first and keep the old endpoint during the agreed migration grace period that began 14 October 2026; do not archive or rename `InternetRadio` until the migration checks are complete. On 18 October 2026 the owner decided to move the source and future development to `RP-Android-Apps`, keeping `InternetRadio` as the update backup; see [`docs/source-migration.md`](docs/source-migration.md). Oor Arivippu 1.0.0 and 1.0.1 were published to both repositories separately and are not part of that four-app bridge batch. See [`docs/repository-migration.md`](docs/repository-migration.md).

## Project documents

- [`PRIVACY.md`](PRIVACY.md) — data flows, permissions and retention.
- [`PUBLISHING.md`](PUBLISHING.md) — current build and release process.
- [`CHANGELOG.md`](CHANGELOG.md) — release history and current scope.
- [`docs/published-release-digests.md`](docs/published-release-digests.md) — verified public APK hashes.
- [`store/README.md`](store/README.md) — store-art and metadata inventory.
