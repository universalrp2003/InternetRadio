# Store-channel status — 13 October 2026

This report describes repository artifacts, not external store acceptance.

## Current distribution

- **GitHub Releases:** all four current stable APK releases are public and remain the verified direct-download channel. Their asset names, sizes and SHA-256 digests are in [`../../docs/published-release-digests.md`](../../docs/published-release-digests.md).
- **Google Play:** CI builds AABs for Live Guard, Ramesh Radio and PulseEQ. This repository does not claim that any of those bundles has been submitted, accepted or published. Live Guard uses all-files access; review the current Play policy and app-specific declaration before submitting it. RP Vocal Remover Studio is currently distributed as a standalone APK and is outside the store-art pipeline.
- **F-Droid:** the `metadata/` directory contains prepared recipes for Live Guard, Ramesh Radio and PulseEQ. Recipe files are not proof of an accepted request or live listing. Check the current F-Droid tracker and repository before reporting external status. RP Vocal Remover Studio has no recipe here.
- **Other third-party stores:** no inclusion or approval claim is made by this repository. Check each store's current policy and listing before referring users there.

## Current F-Droid recipes

| App | Package | Module | Version / code | Source tag |
|---|---|---|---:|---|
| Live Guard (CleanSweep) | `com.universalrp.cleansweep` | `cleaner` | 2.44 / 49 | `live-guard-v2.44` |
| Ramesh Radio | `com.universalrp.tamilnadufm` | `radio` | 1.9 / 9 | `ramesh-radio-v1.9` |
| PulseEQ | `com.universalrp.pulseeq` | `equalizer` | 1.3 / 4 | `pulseeq-v1.3` |

F-Droid builds and signs its own APKs, which may not update a GitHub-signed installation in place. Explain signing-key differences and potential local-data consequences before recommending a channel switch.

## Repository and migration

All current APK updaters and store recipes intentionally use `github.com/universalrp2003/InternetRadio`. Do not change or redirect this repository URL yet. After bridge updates for all four apps have been installed and verified on devices, follow the staged migration in [`../../docs/repository-migration.md`](../../docs/repository-migration.md). Preserve the old release archive and do not delete, replace or rewrite published release assets.
