# Changelog — RP Ideas Android apps

This file indexes the four actively maintained apps. App-specific details, installation notes and verified APK digests are linked below. Historical GitHub Releases, tags and assets remain available even when no longer represented in the active source tree.

## Latest published releases — 14 October 2026

| App | Version | Release notes | RP-Android-Apps | InternetRadio fallback |
|---|---:|---|---|---|
| Live Guard (CleanSweep) | 2.46 / code 51 | [`releases/live-guard-v2.46.md`](releases/live-guard-v2.46.md) | [live-guard-v2.46](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46) | [live-guard-v2.46](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.46) |
| Ramesh Radio | 1.11 / code 11 | [`releases/ramesh-radio-v1.11.md`](releases/ramesh-radio-v1.11.md) | [ramesh-radio-v1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11) | [ramesh-radio-v1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.11) |
| PulseEQ | 1.5 / code 6 | [`releases/pulseeq-v1.5.md`](releases/pulseeq-v1.5.md) | [pulseeq-v1.5](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5) | [pulseeq-v1.5](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.5) |
| RP Vocal Remover Studio | 0.1.11 / code 12 | [`releases/rp-vocal-remover-studio-v0.1.11.md`](releases/rp-vocal-remover-studio-v0.1.11.md) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.11) |

GitHub Actions [run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) passed the four-app build, unit/regression tests, APK and signature checks, and dual-repository publication. Both repositories now serve the four stable APKs. A live public-API check confirmed the destination releases are non-draft/non-prerelease, use canonical release and download URLs, are newer than the bridge versions, and have exactly matching asset sizes and SHA-256 values on both repositories. The updater source order and selection logic prefer `RP-Android-Apps`; this is API/source verification, not an on-device install test of these exact APK bytes. See [`docs/published-release-digests.md`](docs/published-release-digests.md). The earlier candidate-run hashes in [`docs/candidate-build-digests.md`](docs/candidate-build-digests.md) are from a different build and must not be treated as the published asset digests.

## Previous stable bridge releases — 14 October 2026

| App | Version | Release notes | GitHub Release |
|---|---:|---|---|
| Live Guard (CleanSweep) | 2.45 / code 50 | [`releases/live-guard-v2.45.md`](releases/live-guard-v2.45.md) | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) |
| Ramesh Radio | 1.10 / code 10 | [`releases/ramesh-radio-v1.10.md`](releases/ramesh-radio-v1.10.md) | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) |
| PulseEQ | 1.4 / code 5 | [`releases/pulseeq-v1.4.md`](releases/pulseeq-v1.4.md) | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) |
| RP Vocal Remover Studio | 0.1.10 / code 11 | [`releases/rp-vocal-remover-studio-v0.1.10.md`](releases/rp-vocal-remover-studio-v0.1.10.md) | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) |

Publication run **38501139771** built and verified the four bridge APKs and published them on the old `InternetRadio` endpoint so existing installations could discover an increasing-version update. Those earlier release pages, tags and assets remain available and were not overwritten by the new batch.

The Live Guard permission-delta check for 2.45 found no newly added dangerous permission, so no physical-device evidence record was required. The exact CI-generated bridge APK bytes were not installed on a phone during that publication; no exact-build device test is claimed. CI and release validation are not Play Protect approval or an all-device compatibility claim. On 14 October, the owner reported the bridge apps were working and manual update checks completed without errors. The subsequent 2.46 / 1.11 / 1.5 / 0.1.11 batch has now seeded `RP-Android-Apps`; API/source-selection verification is recorded above and in the migration ledger. On-device retrieval/installation of those latest exact bytes remains a separate grace-period check.

Full APK filenames, byte sizes and SHA-256 digests for the current and previous releases are in [`docs/published-release-digests.md`](docs/published-release-digests.md).

## Previous stable releases — 13 October 2026

| App | Version | Release notes | GitHub Release |
|---|---:|---|---|
| Live Guard (CleanSweep) | 2.44 / code 49 | [`releases/live-guard-v2.44.md`](releases/live-guard-v2.44.md) | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) |
| Ramesh Radio | 1.9 / code 9 | [`releases/ramesh-radio-v1.9.md`](releases/ramesh-radio-v1.9.md) | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) |
| PulseEQ | 1.3 / code 4 | [`releases/pulseeq-v1.3.md`](releases/pulseeq-v1.3.md) | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) |
| RP Vocal Remover Studio | 0.1.9 / code 10 | [`releases/rp-vocal-remover-studio-v0.1.9.md`](releases/rp-vocal-remover-studio-v0.1.9.md) | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) |

CI publication run **38380663566** built and verified those releases, including CleanSweep regression tests, package/version/signature checks and public-asset size/SHA-256 verification. The Live Guard permission-delta check found no newly added dangerous permission relative to 2.43. The exact Live Guard CI release APK was not installed during that run; CI is not a phone test or Play Protect approval. The owner separately reported installing all four then-current apps and that they worked on their phone on 13 October 2026; that report is not an all-device compatibility claim. Their release pages, tags, APK assets and original SHA-256 records remain unchanged as historical releases.

## Recent Live Guard history

- **2.44 / code 49:** added update verification, safer APK hand-off and contributor/release links. The permission-delta gate found no new dangerous permission relative to 2.43. See [`releases/live-guard-v2.44.md`](releases/live-guard-v2.44.md).
- **2.43 / code 48:** corrected charging measurements so unsupported zero current is unavailable rather than a false zero-watt reading. See [`releases/v2026.10.12-3.md`](releases/v2026.10.12-3.md).
- **2.42 / code 47:** device-health, voice-selection and monitoring refinements; associated device evidence and scope are linked from the release notes. See [`releases/v2026.10.12-2.md`](releases/v2026.10.12-2.md).
- **2.41 / code 46:** honest charging readings, a local 44 °C warning, and bounded AI output/provider checks. The temperature warning is not an automatic pause threshold. See [`releases/v2026.10.12-1.md`](releases/v2026.10.12-1.md).
- **2.40 / code 45:** removed notification/message-reading capability from the app. See [`releases/v2026.10.11-4.md`](releases/v2026.10.11-4.md) and the [Play Protect installation record](docs/live-guard-play-protect-install-block.md).

Older release notes and device-test records remain in `releases/` and `docs/` as historical records. They describe the scope and test process at the time of those versions and are not current feature or build instructions.

## Project scope

The current source, Gradle build, CI and store preparation cover Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio only. Releases for retired modules are preserved on GitHub as history and are not part of current builds or future release batches. The repository slug stays unchanged until the staged updater migration is complete; see [`docs/repository-migration.md`](docs/repository-migration.md).
