# Staged repository migration plan

**Status: planned, not started.** Keep the existing repository at `github.com/universalrp2003/InternetRadio` until the bridge-update sequence below is complete. The four installed app updater clients depend on this exact repository API/release path and reject HTTP redirects. Do not rename, delete, archive or redirect the current repository now.

## Target

- Public project brand: **RP Ideas**.
- Future repository slug: **`RP-Android-Apps`**.
- Active apps: Live Guard, Ramesh Radio, PulseEQ and RP Vocal Remover Studio.
- Preserve the existing repository's published GitHub Releases, tags and APK assets as historical records. Prefer archiving the old repository after the transition rather than deleting it.

## Phase 0 — keep the current endpoint stable

Until all bridge updates are installed and confirmed:

- Keep every app updater, CI URL assertion, recipe and release link on `universalrp2003/InternetRadio`.
- Publish no URL-only rebuild under an existing version or tag. Any bridge APK is a new, properly versioned, signed release.
- Do not rename the repository or rely on a redirect. Current clients do not follow one.

The owner reported that all four current apps were installed and working on their phone on 13 October 2026. That does not yet mean a bridge update has reached the device; the current updater URL must stay intact until the bridge stage.

## Phase 1 — prepare the destination

Only when the owner authorizes migration:

1. Create `universalrp2003/RP-Android-Apps` as a separate repository under the chosen RP Ideas brand. Do not rename or repurpose the existing repository.
2. Copy the four active source modules, shared code, build scripts, licensing, current documentation and required store metadata. Exclude retired app source and obsolete generated assets.
3. Configure the destination CI, permissions, signing secrets and release-asset verification. Verify its APK download URLs are canonical and stable, and verify all four apps can check the destination's release API.
4. Keep historical releases and tags in the old repository. Do not overwrite their assets or move/delete them as a condition of creating the new repository.

## Phase 2 — publish bridge updates from the old endpoint

Prepare one new release for each app, built from source that changes the updater to the new repository. The bridge releases must first be published on the **old** `InternetRadio` release endpoint so installed versions can discover and download them.

For all four bridge APKs:

- Preserve package ID, signing certificate and upgrade compatibility; increase the app's version code and use a new version/tag.
- Point the post-update stable release check to `universalrp2003/RP-Android-Apps` and validate the destination's canonical release/asset URL, digest, package, version and signer.
- Keep a safe failure path if the new endpoint is unavailable; never silently install or bypass Android's installer or Play Protect.
- Run normal module tests, required Live Guard permission-delta/evidence checks, full CI release validation, and actual update-path checks. Confirm that the bridge APK installs over the old version in place and that it can see a test/current stable release on the new repository.
- Publish all four releases under the old repository only after each app passes its checks. Keep those bridge release pages and assets permanently as history.

## Phase 3 — device confirmation and grace period

The owner must confirm that each of the four bridge versions has been received and installed by its intended devices, that each app opens, and that each new updater reaches the new repository. Record app name, installed version, device/Android version and the observed update result without collecting unrelated personal data.

After all four bridge updates are confirmed installed, keep the old repository active for approximately **one to two months** as a transition/grace period. During this period:

- Leave all old release pages/assets available and do not break old URLs.
- Monitor update checks and reports from any remaining old clients.
- Do not issue current product updates through a stale updater route; publish new active releases at the new repository once bridge behavior is confirmed.
- Extend the grace period if any device or app has not successfully crossed over.

## Phase 4 — retire, preserve history

Only after the grace period and owner confirmation:

1. Stop using the old repository for new development and releases.
2. Archive the existing `InternetRadio` repository rather than deleting it, after checking that its public release pages and APK assets remain reachable.
3. Keep old tags, release notes, APKs and their digests unchanged for historical/reference use. Do not replace old files, erase old tags, or assume an archived repository is a new update source.
4. Update the root documentation and store records to identify `RP-Android-Apps` as the active source, while preserving clear links to the historical release archive.

If any update, signing, release-host or device-compatibility check fails, stop the migration and keep the current repository endpoint active until a safe recovery plan is agreed.
