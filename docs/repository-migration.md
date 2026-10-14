# Staged repository migration plan

**Status — 14 October 2026:** the four-app release batch (Live Guard 2.46, Ramesh Radio 1.11, PulseEQ 1.5 and RP Vocal Remover Studio 0.1.11) is published on both `universalrp2003/RP-Android-Apps` and `universalrp2003/InternetRadio`. GitHub Actions run [38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) passed build, regression and release verification. A live public-API check verified all four destination releases are discoverable, stable, newer than their bridge versions, and have canonical URLs and byte-identical size/SHA-256 metadata to the old-repository copies. Source inspection and the passing updater tests confirm every app checks the new repository first and retains the old fallback. **The owner has now supplied phone screenshots showing update-available notices for all four bridge versions. This confirms on-device update-metadata discovery; the screenshots do not identify which repository supplied each result or show a completed APK download/verification/installation.** Keep `InternetRadio`, its historical release pages, tags and assets intact during the agreed one-to-two-month grace period.

## Target and invariants

- Public project brand: **RP Ideas**.
- New release repository: **`universalrp2003/RP-Android-Apps`**.
- Existing/bridge repository: **`universalrp2003/InternetRadio`**.
- Active apps: Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio. RP Vocal Remover Studio remains standalone.
- The old repository remains public and unarchived during the grace period. Do not rename, delete, archive, redirect, or remove its historical releases, tags or APK assets until the later transition gate has passed.
- The target repository is the first updater source. The old source is a verified fallback. Do not remove the fallback during this grace period.

## Verified public release batch — 14 October 2026

| App | Bridge version → available update | RP-Android-Apps | InternetRadio mirror |
|---|---:|---|---|
| Live Guard | 2.45 → 2.46 | [live-guard-v2.46](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46) | [live-guard-v2.46](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.46) |
| Ramesh Radio | 1.10 → 1.11 | [ramesh-radio-v1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11) | [ramesh-radio-v1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.11) |
| PulseEQ | 1.4 → 1.5 | [pulseeq-v1.5](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5) | [pulseeq-v1.5](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.5) |
| RP Vocal Remover Studio | 0.1.10 → 0.1.11 | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.11) |

The post-publication API check independently checked each expected tag in both repositories: `draft=false`, `prerelease=false`, exact canonical release page, one uploaded APK with the expected filename, canonical download URL, valid SHA-256, and equal size/digest across repositories. Each destination version is numerically newer than the corresponding bridge version. Exact public APK sizes and SHA-256 digests are recorded in [`published-release-digests.md`](published-release-digests.md).

The updater source is shared by Ramesh Radio, PulseEQ and RP Vocal Remover Studio; Live Guard has its own implementation. Both paths enumerate the ordered sources `RP-Android-Apps` then `InternetRadio`, accept only stable releases on canonical GitHub paths with valid size/digest metadata, and choose a newer version. If a version is present on both repositories, the new source wins the equal-version tie. The signed APK is then rechecked for digest, package, version and pinned signer before Android's installer hand-off. The build run's source/unit tests passed, and the live API check confirms all four matching release records. Owner phone screenshots now confirm update notices for all four bridge versions. They do not identify the supplying repository or show a completed download/hash check/install of these exact published bytes; that artifact-transfer validation remains open.

## Phase 1 — prepare and seed the destination (publication done)

The owner created `RP-Android-Apps` as a separate public repository with a `main` branch, README and GPL-3.0 license. The active Android source/build remains on `InternetRadio` for now; the destination is seeded with public stable APK releases and assets, not a source-code migration.

1. The destination was public, initialized, unarchived and reachable at publication time.
2. The owner configured the scoped Actions write secret in the source repository; no secret value is recorded here.
3. Run [38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) published and verified all four stable releases to both repositories. The target release assets have matching digests/sizes and are now visible through the public Releases API.
4. Keep the source repository authoritative for builds until migration ownership is intentionally changed. Never overwrite an existing target tag or reuse an app version for changed APK bytes.

## Phase 2 — bridge releases on the old endpoint (complete)

The initial bridge batch was intentionally published on `InternetRadio` so already-installed pre-migration clients could find an increasing-version update:

| App | Bridge version / code | Old-endpoint release |
|---|---:|---|
| Live Guard | 2.45 / 50 | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) |
| Ramesh Radio | 1.10 / 10 | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) |
| PulseEQ | 1.4 / 5 | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) |
| RP Vocal Remover Studio | 0.1.10 / 11 | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) |

Bridge publication run [38501139771](https://github.com/universalrp2003/InternetRadio/actions/runs/38501139771) passed its build, unit/regression, package/version/signer, digest and Live Guard permission-delta checks. The owner confirmed on 14 October that all four bridge apps work and manual update checks finish without errors. This remains owner-reported feedback: the CI did not install hash-identified APK bytes on a phone, and no device model/OS or exact-build digest evidence was provided. Android's per-app **Install unknown apps** access and confirmation remain required for direct APK installs.

## Phase 3 — grace period (in progress)

The one-to-two-month grace period starts **14 October 2026**, the owner's bridge-app confirmation date. Review no earlier than around **14 November**; plan a migration decision around **14 December 2026 or later** if any check remains incomplete. Extend the period if needed.

During this period:

- Keep `InternetRadio` available and unarchived. Do not rename it or rely on a repository redirect; existing installed clients use its exact API/release path and reject redirects.
- Preserve every historical `InternetRadio` release page, tag and APK asset. The new latest release batch was added to both repositories; earlier historical assets must remain unchanged.
- Keep the `InternetRadio` updater fallback in all four apps while any device may still be on a bridge build.
- **On-device update discovery confirmed 14 October:** owner-provided screenshots show installed → offered versions Live Guard 2.45 → 2.46, Ramesh Radio 1.10 → 1.11, PulseEQ 1.4 → 1.5, and RP Vocal Remover Studio 0.1.10 → 0.1.11. The screens show the update notices, not a completed download or install. They do not expose which repository supplied each candidate; public API checks and code confirm `RP-Android-Apps` is checked first and wins an equal-version tie, but source-specific device response is not separately visible in this evidence. Keep exact-byte download/hash verification and installation unclaimed until performed.
- Do not remove the old updater source as part of another feature update. Make that a separately versioned, later migration release after grace-period validation and owner confirmation.

## Phase 4 — retire while preserving history (not started)

Only after the grace period and the device/update validations are complete and the owner confirms:

1. Prepare, build and publish a later updater-only transition that removes the `InternetRadio` updater source while retaining all APK verification checks.
2. Stop using `InternetRadio` for new development/releases only after the target is proven to serve updates to all four apps.
3. Archive—not delete—the old repository, after confirming its historical public release pages and APK assets remain reachable.
4. Preserve old tags, release notes, APKs and their digest records. Do not replace old release assets or assume an archived repository is still an update source.
5. Update active-source documentation while keeping a clear link to the historical archive.

If any signing, update-discovery, release-host or device-compatibility check fails, keep the old endpoint available and defer archival until a safe recovery plan is agreed.
