# Staged repository migration plan

**Status — 15 October 2026:** the four-app release batch (Live Guard 2.46, Ramesh Radio 1.11, PulseEQ 1.5 and RP Vocal Remover Studio 0.1.11) is published on both `universalrp2003/RP-Android-Apps` and `universalrp2003/InternetRadio`. GitHub Actions run [38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) passed build, regression and release verification. A live public-API check verified all four destination releases are discoverable, stable, newer than their bridge versions, and have canonical URLs and byte-identical size/SHA-256 metadata to the old-repository copies. Source inspection and the passing updater tests confirm every app checks the new repository first and retains the old fallback. **Owner-supplied phone screenshots confirm update notices for all four bridge versions. Later screenshots show Live Guard reporting 2.46 installed, Ramesh Radio at Play Protect's “This app looks safe” screen, and PulseEQ at Android's update-confirmation screen. They do not identify the source repository or tie the installed/pending APK bytes to the published SHA-256 digests; final installation is not shown for every app.**

On 15 October 2026, Oor Arivippu 1.0.0 / code 2 was separately published to both repositories by [standalone release run 38748688974](https://github.com/universalrp2003/InternetRadio/actions/runs/38748688974); this is a fifth active product, not a fifth member of the four-app bridge batch. The API confirmed identical public assets (12,179,013 bytes, SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`) and canonical URLs. The owner reports successful testing on two devices and says the stable app is working. Screenshots show the v1.0.0 (code 2) package, Play Protect's “This app looks safe” screen, and the running app's About/update screen at v1.0.0, but do not show the APK SHA-256; the evidence is not hash-linked to the published bytes. On 16 October 2026, Oor Arivippu 1.0.1 / code 3 was published the same standalone way by [run 38826899278](https://github.com/universalrp2003/InternetRadio/actions/runs/38826899278), with identical public assets on both repositories (12,195,397 bytes, SHA-256 `ec10c7369807748c9a5608f1a5c6a37eff5f8e1f7433cc16e984c75d8958457d`); the 1.0.0 releases are unchanged. Later that day, Oor Arivippu 1.0.2 / code 4 was published the same way by [run 38840264469](https://github.com/universalrp2003/InternetRadio/actions/runs/38840264469), again with identical public assets on both repositories (12,195,401 bytes, SHA-256 `bffdf37f525becc3a631c83988e56ca9881e620b76fd2e60bd7ce5b31efdd78b`); the 1.0.0 and 1.0.1 releases are unchanged. Keep `InternetRadio`, its historical release pages, tags and assets intact during the agreed one-to-two-month grace period.

## Target and invariants

- Public project brand: **RP Ideas**.
- New release repository: **`universalrp2003/RP-Android-Apps`**.
- Existing/bridge repository: **`universalrp2003/InternetRadio`**.
- Active apps: Live Guard, Ramesh Radio, PulseEQ, RP Vocal Remover Studio and Oor Arivippu. The migration grace period and bridge table below cover the four legacy bridge clients; Oor Arivippu is a separately released fifth product.
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

The updater source is shared by Ramesh Radio, PulseEQ and RP Vocal Remover Studio; Live Guard has its own implementation. Both paths enumerate the ordered sources `RP-Android-Apps` then `InternetRadio`, accept only stable releases on canonical GitHub paths with valid size/digest metadata, and choose a newer version. If a version is present on both repositories, the new source wins the equal-version tie. The signed APK is then rechecked for digest, package, version and pinned signer before Android's installer hand-off. The build run's source/unit tests passed, and the live API check confirms all four matching release records. Owner phone screenshots confirm update notices for all four bridge versions. A later screen reports Live Guard 2.46 installed; Ramesh Radio reaches Play Protect's safe-to-install screen and PulseEQ reaches Android's update confirmation. These show on-device updater hand-off, but not the supplying repository or a hash-linked published-APK installation; screenshots do not confirm the final install for every app.

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

## Source move to RP-Android-Apps (owner decision, 18 October 2026)

The owner chose to move the code and future development to `RP-Android-Apps` now, while `InternetRadio` keeps serving as the update backup until the review below. The apps and `InternetRadio`'s releases do not change; Phases 3 and 4 below are unchanged. The import is done by a new Arena session on `RP-Android-Apps`, following [`source-migration.md`](source-migration.md) (secrets, import without tags, workflow changes, verification).

## Phase 3 — grace period (in progress)

The one-to-two-month grace period starts **14 October 2026**, the owner's bridge-app confirmation date. Review no earlier than around **14 November**; plan a migration decision around **14 December 2026 or later** if any check remains incomplete. Extend the period if needed.

During this period:

- Keep `InternetRadio` available and unarchived. Do not rename it or rely on a repository redirect; existing installed clients use its exact API/release path and reject redirects.
- Preserve every historical `InternetRadio` release page, tag and APK asset. The new latest release batch was added to both repositories; earlier historical assets must remain unchanged.
- Keep the `InternetRadio` updater fallback in all four apps while any device may still be on a bridge build.
- **On-device update checks — 14 October:** owner-provided screenshots show installed → offered versions Live Guard 2.45 → 2.46, Ramesh Radio 1.10 → 1.11, PulseEQ 1.4 → 1.5, and RP Vocal Remover Studio 0.1.10 → 0.1.11. A later screenshot shows Live Guard reporting 2.46 installed; the Ramesh Radio screenshot reaches Play Protect's safe-to-install page and PulseEQ reaches Android's update confirmation. These confirm discovery and updater hand-off, but do not show repository origin, and no screenshot records a digest tied to the released APK bytes. Do not treat the Ramesh/PulseEQ confirmation prompts as proof that installation finished. The public API and code establish `RP-Android-Apps` is checked first and wins an equal-version tie; source-specific device response is not independently shown.
- Do not remove the old updater source as part of another feature update. Make that a separately versioned, later migration release after grace-period validation and owner confirmation.

## Phase 4 — retire while preserving history (not started)

Only after the grace period and the device/update validations are complete and the owner confirms:

1. Prepare, build and publish a later updater-only transition that removes the `InternetRadio` updater source while retaining all APK verification checks.
2. Stop using `InternetRadio` for new development/releases only after the target is proven to serve updates to all four apps.
3. Archive—not delete—the old repository, after confirming its historical public release pages and APK assets remain reachable.
4. Preserve old tags, release notes, APKs and their digest records. Do not replace old release assets or assume an archived repository is still an update source.
5. Update active-source documentation while keeping a clear link to the historical archive.

If any signing, update-discovery, release-host or device-compatibility check fails, keep the old endpoint available and defer archival until a safe recovery plan is agreed.
