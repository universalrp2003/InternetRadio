# Build and release guide — RP Ideas

This guide covers the four active modules in the current repository. GitHub Releases are the verified direct-download channel. A successful CI build is required before publication; CI success is not Play Protect approval or a device-compatibility certification.

## Latest published release batch — 14 October 2026

| App | Gradle module | Version / code | Release tag | APK |
|---|---|---:|---|---|
| Live Guard / CleanSweep | `cleaner` | 2.46 / 51 | `live-guard-v2.46` | `CleanSweep-v2.46.apk` |
| Ramesh Radio | `radio` | 1.11 / 11 | `ramesh-radio-v1.11` | `Ramesh-Radio-v1.11.apk` |
| PulseEQ | `equalizer` | 1.5 / 6 | `pulseeq-v1.5` | `PulseEQ-v1.5.apk` |
| RP Vocal Remover Studio | `vocalremover` | 0.1.11 / 12 | `rp-vocal-remover-studio-v0.1.11` | `RP-Vocal-Remover-Studio-v0.1.11.apk` |

[Publication run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) passed the builds, unit/regression suites, APK checks and dual-repository release verification. All four stable releases are published on both [`RP-Android-Apps`](https://github.com/universalrp2003/RP-Android-Apps/releases) and [`InternetRadio`](https://github.com/universalrp2003/InternetRadio/releases). The published asset digests—not the earlier build-only candidate hashes—are recorded in [`docs/published-release-digests.md`](docs/published-release-digests.md). The prior candidate run [38514595315](https://github.com/universalrp2003/InternetRadio/actions/runs/38514595315) remains a distinct build and is documented separately in [`docs/candidate-build-digests.md`](docs/candidate-build-digests.md).

The previously published releases, tags and assets for retired modules are retained as history. They are not built or included in new release batches.

## CI build

The GitHub Actions workflow `.github/workflows/build-apk.yml` builds all four debug and signed release APKs, runs unit and required CleanSweep regression tests, checks localization and source contracts, validates the package/version/minimum SDK/permissions/icons/signatures, and verifies release asset size and SHA-256 against the local APK bytes.

The workflow also produces Android App Bundles for `cleaner`, `radio` and `equalizer`. RP Vocal Remover Studio is a standalone direct-download APK and has no AAB in this workflow.

Local commands (JDK 17, Android SDK and NDK required):

```sh
./gradlew :cleaner:testDebugUnitTest :cleaner:testReleaseUnitTest \
  :radio:testDebugUnitTest :radio:testReleaseUnitTest \
  :equalizer:testDebugUnitTest :equalizer:testReleaseUnitTest \
  :vocalremover:testDebugUnitTest :vocalremover:testReleaseUnitTest

./gradlew :cleaner:assembleRelease :radio:assembleRelease \
  :equalizer:assembleRelease :vocalremover:assembleRelease
```

## Verified GitHub Releases

1. Update the relevant module's `versionName` and `versionCode`; do not reuse a release version for changed APK bytes.
2. Update that app's notes in `releases/` and `CHANGELOG.md`. State plainly whether those exact APK bytes were installed on a phone; never turn a CI pass into a device-test claim.
3. Push the verified changes to the authorized branch. For a normal batch publication, the workflow's branch trigger requires `[publish]` in the commit message. The 14 October release batch used this push path (run [38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254)); the workflow creates the four version-specific app release tags from the Gradle versions. A manual batch tag such as `v2026.10.14` is only a workflow input, not an additional app-release tag.
4. The workflow fails closed unless `universalrp2003/RP-Android-Apps` is public, initialized, unarchived and reachable. Before publishing, configure the source repository Actions secret `RP_ANDROID_APPS_RELEASE_TOKEN` with `contents:write` access limited to `RP-Android-Apps`; never paste the credential into chat. If it is absent or invalid, the release step fails before creating any draft, so installed clients do not receive a bridge release before the new source is seeded.
5. **One-time bridge seeding (completed):** the earlier bridge releases were already mirrored by publication run [38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254). Do not rerun `mirror_existing=true` for tags that now exist; the workflow intentionally refuses to overwrite a target tag. For a future migration-only mirror, verify that the target tags are new and run the workflow's mirror path only with owner authorization.
6. Wait for all build, regression, permission, package, signer and asset checks to pass. The workflow stages the four releases as drafts, verifies size and SHA-256 on every configured destination, promotes only after draft checks, and verifies public URLs and asset digests again.
7. Confirm each release is stable (not a draft or prerelease), each canonical release page contains the expected APK, and both repositories' copies match the recorded ledger in [`docs/published-release-digests.md`](docs/published-release-digests.md).

All four current stable releases (Live Guard 2.46, Ramesh Radio 1.11, PulseEQ 1.5 and RP Vocal Remover Studio 0.1.11) are now published on both `universalrp2003/RP-Android-Apps` and `universalrp2003/InternetRadio`. Public API validation confirmed each destination release is stable, its canonical asset URL/name/version is discoverable, and its size and SHA-256 match the old-repository copy. Source order and version-selection checks passed in the release build; actual on-device retrieval/installation of these exact bytes remains to be confirmed. The owner reported the bridge apps work and manual checks complete without errors. The one-to-two-month grace period began on 14 October 2026: keep the old repository, historical release pages, tags and assets intact, and do not rename or archive it before grace-period/device validation. See [`docs/repository-migration.md`](docs/repository-migration.md) for the current gate and sequence.

### Live Guard permission gate

The release workflow compares the merged CleanSweep APK manifest with the most recently published Live Guard release. A newly added dangerous permission requires a truthful physical-device evidence record that identifies the exact tested APK bytes. If an evidence record exists, only its matching bytes may be released. Missing or unreadable prior-release data fails closed. App-op/signature permissions are not treated as Android runtime-dangerous permissions.

No release process disables Play Protect or bypasses Android's installer. Users may need to grant the per-app **Install unknown apps** permission in Settings, and Android shows its own confirmation for each installation.

## Store metadata

The active store-art pipeline covers CleanSweep, Ramesh Radio and PulseEQ. F-Droid metadata is present for those three modules; it is preparation, not a claim that F-Droid has accepted or published them. RP Vocal Remover Studio currently ships through its GitHub Release only. See [`store/README.md`](store/README.md).
