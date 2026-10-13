# Changelog — RP Ideas Android apps

This file indexes the four actively maintained apps. App-specific details, installation notes and verified APK digests are linked below. Historical GitHub Releases, tags and assets remain available even when no longer represented in the active source tree.

## Current stable releases — 13 October 2026

| App | Version | Release notes | GitHub Release |
|---|---:|---|---|
| Live Guard (CleanSweep) | 2.44 / code 49 | [`releases/live-guard-v2.44.md`](releases/live-guard-v2.44.md) | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) |
| Ramesh Radio | 1.9 / code 9 | [`releases/ramesh-radio-v1.9.md`](releases/ramesh-radio-v1.9.md) | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) |
| PulseEQ | 1.3 / code 4 | [`releases/pulseeq-v1.3.md`](releases/pulseeq-v1.3.md) | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) |
| RP Vocal Remover Studio | 0.1.9 / code 10 | [`releases/rp-vocal-remover-studio-v0.1.9.md`](releases/rp-vocal-remover-studio-v0.1.9.md) | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) |

CI publication run **38380663566** built and verified the four releases, including CleanSweep regression tests, package/version/signature checks and public-asset size/SHA-256 verification. The Live Guard permission-delta check found no newly added dangerous permission relative to 2.43. The exact Live Guard CI release APK was not installed during that run; CI is not a phone test or Play Protect approval. The owner separately reported installing all four current apps and that they worked on their phone on 13 October 2026; that report is not an all-device compatibility claim.

Full APK filenames, byte sizes and SHA-256 digests: [`docs/published-release-digests.md`](docs/published-release-digests.md).

## Recent Live Guard history

- **2.43 / code 48:** corrected charging measurements so an unsupported zero current is shown as unavailable rather than as a false zero-watt reading. See [`releases/v2026.10.12-3.md`](releases/v2026.10.12-3.md).
- **2.42 / code 47:** device-health, voice-selection and monitoring refinements; associated device evidence and scope are linked from the release notes. See [`releases/v2026.10.12-2.md`](releases/v2026.10.12-2.md).
- **2.41 / code 46:** honest charging readings, a local 44 °C warning, and bounded AI output/provider checks. The temperature warning is not an automatic pause threshold. See [`releases/v2026.10.12-1.md`](releases/v2026.10.12-1.md).
- **2.40 / code 45:** removed notification/message-reading capability from the app. See [`releases/v2026.10.11-4.md`](releases/v2026.10.11-4.md) and the [Play Protect installation record](docs/live-guard-play-protect-install-block.md).

Older release notes and device-test records remain in `releases/` and `docs/` as historical records. They describe the scope and test process at the time of those versions and are not current feature or build instructions.

## Project scope

The current source, Gradle build, CI and store preparation cover Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio only. Releases for retired modules are preserved on GitHub as history and are not part of current builds or future release batches. The repository slug stays unchanged until the staged updater migration is complete; see [`docs/repository-migration.md`](docs/repository-migration.md).
