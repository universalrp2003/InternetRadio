# Staged repository migration plan

**Status: all four increasing-version bridge APKs were published on `universalrp2003/InternetRadio` on 14 October 2026 after destination preflight and CI/release checks passed. On 14 October the owner confirmed all four bridge apps are working and manual update checks finish without errors; the one-to-two-month grace period starts on that confirmation date. The public destination `universalrp2003/RP-Android-Apps` (created with a `main` branch, README and GPL-3.0 license) still has no app releases, so fetching a newer release from the destination remains to be verified during the grace period.** Keep the existing repository at `github.com/universalrp2003/InternetRadio` available throughout the transition. Installed releases use that exact API/release path and reject HTTP redirects. The bridge clients query `RP-Android-Apps` first and then `InternetRadio`; both sources are retained. Do not rename, delete, archive or redirect the current repository now.

## Target

- Public project brand: **RP Ideas**.
- Future repository slug: **`RP-Android-Apps`**.
- Active apps: Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio.
- Preserve the existing repository's published GitHub Releases, tags and APK assets as historical records. Prefer archiving the old repository after the transition rather than deleting it.

## Phase 0 — keep both signed release sources safe

- Existing installed releases continue to use `universalrp2003/InternetRadio` until their bridge APK is installed.
- Bridge APKs check `universalrp2003/RP-Android-Apps` first and the still-live `universalrp2003/InternetRadio` second. Each candidate must pass the same HTTPS canonical-path, stable-release, asset-size, SHA-256, package/version and pinned-signer checks.
- Keep the old source enabled during the grace period. Do not publish a URL-only rebuild under an existing version or tag; every changed APK needs a new version and tag.
- Do not rename the old repository or rely on a redirect. Existing clients reject redirects.

The owner had reported that all four then-current apps were installed and working on 13 October 2026; that earlier report did not cover the bridge builds. On 14 October, after the bridge releases were published, the owner confirmed all four apps were working and manual update checks completed without errors. The destination still has no app releases, so an actual update fetched from `RP-Android-Apps` has not yet been demonstrated.

## Phase 1 — prepare the destination

The owner created `universalrp2003/RP-Android-Apps` as a separate public repository on 14 October 2026. Its `main` branch currently contains the initial README and GPL-3.0 license; no app releases or APK assets have been published there yet.

1. **Done:** create the public repository under the RP Ideas brand; `InternetRadio` remains unchanged.
2. Decide and prepare the destination's ongoing source/build ownership before any source-repository move. The old repository remains authoritative during the bridge.
3. **Bridge preflight and publication done:** the workflow checked that the destination is public, initialized, unarchived and reachable, then published and verified all four bridge APKs on the old endpoint. The owner has since confirmed the bridge apps work; seed and verify new-repository releases during the grace period. The destination currently has no app releases.
4. Keep historical releases and tags in the old repository. Do not overwrite or delete their assets as a condition of creating the new repository.

## Phase 2 — publish bridge updates from the old endpoint (complete)

The four increasing-version bridge APKs are published on the **old** `InternetRadio` endpoint so already-installed versions can discover them. Each bridge build checks `RP-Android-Apps` first and retains `InternetRadio` as a verified fallback.

| App | Bridge version / code | Published release |
|---|---:|---|
| Live Guard | 2.45 / 50 | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) |
| Ramesh Radio | 1.10 / 10 | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) |
| PulseEQ | 1.4 / 5 | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) |
| RP Vocal Remover Studio | 0.1.10 / 11 | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) |

GitHub Actions run [38501139771](https://github.com/universalrp2003/InternetRadio/actions/runs/38501139771), from source commit `85afc48c20edd86d19a81e65cdc307c29535ab1d`, passed the builds, regression suites, APK package/version/signer and digest checks, Live Guard permission-delta gate, four-release staging/publication and public asset verification. All four releases are stable, with their published byte sizes and SHA-256 values recorded in [`published-release-digests.md`](published-release-digests.md). The Live Guard gate found no newly added dangerous permission relative to 2.44, so no physical-device evidence record was required. These exact CI APKs were not installed on a phone during publication; no exact-build device test is claimed.

**Owner confirmation received on 14 October 2026:** the owner reports all four bridge apps are working and manual update checks complete without errors. The updater UI does not expose a separate status for each repository. Source inspection confirms all four clients query `RP-Android-Apps` first and then `InternetRadio`, selecting the newest valid release while preserving the old source. Because the destination still has no app releases, this does not yet demonstrate fetching an update from the new repository. The publication run did not install hash-identified APK bytes on a phone; record this as owner-reported device confirmation, not an exact-digest device-test result. Android's per-app **Install unknown apps** permission and installer confirmation remain required. Keep all four old-endpoint release pages, tags and assets unchanged.

## Phase 3 — grace period (in progress)

On **14 October 2026**, the owner confirmed that all four bridge apps are working and manual update checks complete without errors. The UI does not show per-repository status; the clients query `RP-Android-Apps` first and then `InternetRadio`. Since the destination has no app releases yet, confirming a successful fetch from the new repository remains a grace-period validation task. No device model/Android version or hash-linked exact-build evidence was supplied, so retain this as owner-reported confirmation rather than an exact-digest test claim.

**Grace period start: 14 October 2026.** Keep both repository sources in the clients for approximately **one to two months**—review no earlier than around 14 November and plan a migration decision around 14 December 2026 or later if any validation remains incomplete. Extend the period if needed. During this period:

- Leave every old release page, tag and APK asset available; do not break old URLs.
- Publish new active releases at `RP-Android-Apps` and confirm the new-first lookup works for all four apps. This end-to-end new-source check remains pending while the destination has no app releases.
- Keep `InternetRadio` as the verified fallback while any device may still be using the bridge.
- Only after the grace period and successful destination/update validation, prepare a later release that removes the old updater source. Do not combine this with the first bridge.

## Phase 4 — retire, preserve history

Only after the grace period and owner confirmation:

1. Publish and verify the later updater-only transition that removes the old `InternetRadio` source while retaining all APK verification checks.
2. Stop using the old repository for new development and releases.
3. Archive the existing `InternetRadio` repository rather than deleting it, after checking that its public release pages and APK assets remain reachable.
4. Keep old tags, release notes, APKs and their digests unchanged for historical/reference use. Do not replace old files, erase old tags, or assume an archived repository is an update source.
5. Update root documentation and store records to identify `RP-Android-Apps` as the active source, while preserving clear links to the historical archive.

If any update, signing, release-host or device-compatibility check fails, stop the migration and keep the current repository endpoint active until a safe recovery plan is agreed.
