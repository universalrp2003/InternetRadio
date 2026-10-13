# RP Ideas — Android apps

Four actively maintained, open-source Android apps developed by **Ramesh prathap .R** with shared project contributions, including software assistance from **Arena.ai Agent Mode**. The apps offer Tamil-first interfaces with an in-app English option. Current minimum Android versions are listed below; device-dependent features are described honestly in each app's documentation.

| App | Module | Current release | Android | Purpose |
|---|---|---:|---:|---|
| **Live Guard (CleanSweep)** | `cleaner` | [2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) | 8.0+ (API 26) | Phone cleanup, health and security review, network tools, and optional assistance. |
| **Ramesh Radio** | `radio` | [1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) | 8.0+ (API 26) | Tamil and world radio, local playback, and app-scoped equalization. |
| **PulseEQ** | `equalizer` | [1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) | 8.0+ (API 26) | Twenty-band audio DSP, presets, spectrum view, and local playback. |
| **RP Vocal Remover Studio** | `vocalremover` | [0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) | 10+ (API 29) | Local vocal reduction with WAV output and optional MP3 export. |

## Install and update

Download each signed APK from its linked GitHub Release page. The apps check for newer stable releases and verify the published APK metadata before offering an update. Downloading does not install silently: Android may require the per-app **Install unknown apps** permission and always shows its installer confirmation. Play Protect and Android's signature checks are not bypassed.

For download filenames, sizes and verified SHA-256 values, see [`docs/published-release-digests.md`](docs/published-release-digests.md). Release pages are the authoritative source for current APKs and release notes.

> **Live Guard note:** the release permission-delta gate found no newly added dangerous permission for 2.44, so a physical-device evidence record was not required for that release. The exact CI release bytes were not installed during that publication run. See the [installation advisory](docs/live-guard-play-protect-install-block.md) and [release notes](releases/live-guard-v2.44.md).

## Privacy and audio

- Live Guard stores its local settings and optional histories on-device. Optional AI, web, hash and network tools contact the selected third-party services when used; see [`PRIVACY.md`](PRIVACY.md) before enabling them.
- Ramesh Radio contacts the selected station to stream and searches the public station directory only when requested. Local songs stay on the device.
- PulseEQ processes audio locally. Its optional update check contacts GitHub; system-wide effects remain device/ROM-dependent.
- RP Vocal Remover Studio downloads its checksum-pinned model from the upstream GitHub release. Selected songs are processed on-device; the app has no selected-audio upload path. The first output is a WAV, the optional second pass is user-started, and MP3 export is lossy while the WAV is retained.

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
```

GitHub Actions performs the full build, localization/source checks, CleanSweep regression suite, package/version/signature verification and release-asset digest checks. The workflow also generates Play bundles for CleanSweep, Ramesh Radio and PulseEQ; RP Vocal Remover Studio is distributed as a standalone APK.

## Project scope and repository URL

The active Gradle build, CI, store metadata and documentation cover the four apps above. Earlier app modules were retired from the active source tree; their already-published GitHub Releases and tags are retained as historical records and are not built by current CI.

The repository URL is intentionally still `github.com/universalrp2003/InternetRadio`: the currently installed app versions use that URL for update checks and do not follow repository-rename redirects. A staged migration plan is recorded in [`docs/repository-migration.md`](docs/repository-migration.md). Do not rename or delete the current repository until bridge updates for all four apps have been installed and verified.

## Project documents

- [`PRIVACY.md`](PRIVACY.md) — data flows, permissions and retention.
- [`PUBLISHING.md`](PUBLISHING.md) — current build and release process.
- [`CHANGELOG.md`](CHANGELOG.md) — release history and current scope.
- [`docs/published-release-digests.md`](docs/published-release-digests.md) — verified public APK hashes.
- [`store/README.md`](store/README.md) — store-art and metadata inventory.
