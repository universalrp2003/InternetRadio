# Staged repository migration plan

**Status: bridge preparation authorized; destination `universalrp2003/RP-Android-Apps` was created as a public repository on 14 October 2026, with a `main` branch, README and GPL-3.0 license. It has no app releases yet.** Keep the existing repository at `github.com/universalrp2003/InternetRadio` available throughout the transition. Installed releases use that exact API/release path and reject HTTP redirects. The bridge clients check `RP-Android-Apps` first and retain `InternetRadio` as a verified fallback. Do not rename, delete, archive or redirect the current repository now.

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

The owner reported that all four current apps were installed and working on their phone on 13 October 2026. That does not yet mean a dual-source bridge update has reached the device. The public destination repo now exists but has no app releases; the bridge APKs still require fresh increasing version codes, a passing CI/release gate and a confirmed update path before publication.
## Phase 1 — prepare the destination

The owner created `universalrp2003/RP-Android-Apps` as a separate public repository on 14 October 2026. Its `main` branch currently contains the initial README and GPL-3.0 license; no app releases or APK assets have been published there yet.

1. **Done:** create the public repository under the RP Ideas brand; `InternetRadio` remains unchanged.
2. Decide and prepare the destination's ongoing source/build ownership before any source-repository move. The old repository remains authoritative during the bridge.
3. Configure release permissions and asset verification. Before a bridge release, verify that the public destination release API is reachable. Publish the bridge APKs on the old endpoint first; seed/verify new-repository releases as part of the staged transition so every bridge can demonstrate update discovery.
4. Keep historical releases and tags in the old repository. Do not overwrite or delete their assets as a condition of creating the new repository.

## Phase 2 — publish bridge updates from the old endpoint

Prepare one new release for each app. The bridge APKs must be published on the **old** `InternetRadio` release endpoint so installed versions can discover and download them. After installation, each updater checks both repositories, preferring the new one but falling back to the old one during migration.

For all four bridge APKs:

- Preserve package ID, signing certificate and upgrade compatibility; increase the app's version code and use a new version/tag.
- Validate both repositories' canonical release/asset URLs, stable status, digest, package, version and signer. A missing or unavailable destination must not suppress a valid old-repository update.
- Keep the Android installer confirmation and Play Protect in control; never install silently or bypass them.
- Run module tests, Live Guard permission-delta/evidence checks, full CI release validation and actual update-path checks. Install each bridge over the old version and confirm it can discover a strictly newer version in the destination repository.
- Publish all four old-repository bridge releases only after checks pass. Keep their release pages and assets permanently as history.

## Phase 3 — device confirmation and grace period

The owner must confirm that each of the four bridge versions has been received and installed by its intended devices, that each app opens, and that each new updater reaches the new repository. Record app name, installed version, device/Android version and the observed update result without collecting unrelated personal data.

After all four bridge updates are confirmed installed, keep both repository URLs in the clients for approximately **one to two months** as a transition/grace period (extend it if needed). During this period:

- Leave every old release page and APK asset available; do not break old URLs.
- Publish new active releases at `RP-Android-Apps` and confirm the new-first lookup works for all four apps.
- Keep `InternetRadio` as the verified fallback while any device may still be using the bridge.
- Only after the grace period and owner confirmation, prepare a later release that removes the old updater source. Do not combine this with the first bridge.

## Phase 4 — retire, preserve history

Only after the grace period and owner confirmation:

1. Publish and verify the later updater-only transition that removes the old `InternetRadio` source while retaining all APK verification checks.
2. Stop using the old repository for new development and releases.
3. Archive the existing `InternetRadio` repository rather than deleting it, after checking that its public release pages and APK assets remain reachable.
4. Keep old tags, release notes, APKs and their digests unchanged for historical/reference use. Do not replace old files, erase old tags, or assume an archived repository is an update source.
5. Update root documentation and store records to identify `RP-Android-Apps` as the active source, while preserving clear links to the historical archive.

If any update, signing, release-host or device-compatibility check fails, stop the migration and keep the current repository endpoint active until a safe recovery plan is agreed.
