# Build and release guide — RP Ideas

This guide covers the four active modules in the current repository. GitHub Releases are the verified direct-download channel. A successful CI build is required before publication; CI success is not Play Protect approval or a device-compatibility certification.

## Release candidates awaiting publication

| App | Gradle module | Candidate version | Intended release tag | Expected APK |
|---|---|---:|---|---|
| Live Guard / CleanSweep | `cleaner` | 2.46 | `live-guard-v2.46` | `CleanSweep-v2.46.apk` |
| Ramesh Radio | `radio` | 1.11 | `ramesh-radio-v1.11` | `Ramesh-Radio-v1.11.apk` |
| PulseEQ | `equalizer` | 1.5 | `pulseeq-v1.5` | `PulseEQ-v1.5.apk` |
| RP Vocal Remover Studio | `vocalremover` | 0.1.11 | `rp-vocal-remover-studio-v0.1.11` | `RP-Vocal-Remover-Studio-v0.1.11.apk` |

These four are candidates, not published releases. CI run [38513305483](https://github.com/universalrp2003/InternetRadio/actions/runs/38513305483) passed the build, unit/regression, package, signer and APK checks, but publication failed before any drafts were created because the `RP_ANDROID_APPS_RELEASE_TOKEN` Actions secret is missing. The latest published versions remain Live Guard 2.45, Ramesh Radio 1.10, PulseEQ 1.4 and RP Vocal Remover Studio 0.1.10. After configuring the secret, manually dispatch **Build APK** on `arena/a56e1b40-internetradio` with `release_tag=v2026.10.14` to build and publish this candidate batch.

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
3. Push the verified changes to the authorized branch. For a normal batch publication, the workflow's branch trigger requires `[publish]` in the commit message. The staged bridge batch used a manual workflow dispatch with `release_tag=v2026.10.14` (run [38501139771](https://github.com/universalrp2003/InternetRadio/actions/runs/38501139771)). This is a batch-dispatch identifier, not an extra Git tag; the workflow creates the four version-specific app release tags from the Gradle versions.
4. The workflow fails closed unless `universalrp2003/RP-Android-Apps` is public, initialized, unarchived and reachable. Before publishing, configure the source repository Actions secret `RP_ANDROID_APPS_RELEASE_TOKEN` with `contents:write` access limited to `RP-Android-Apps`; never paste the credential into chat. If it is absent or invalid, the release step fails before creating any draft, so installed clients do not receive a bridge release before the new source is seeded.
5. If an earlier bridge batch has already been published on `InternetRadio` without the target secret, run `Build APK` manually on this branch with `mirror_existing=true`. The workflow downloads each existing public APK, verifies its source size and SHA-256, then mirrors those exact bytes into target drafts and promotes them only after both sides pass verification; old releases, tags and assets are not changed. This mode requires the destination secret and refuses to overwrite existing target tags.
6. Wait for all build, regression, permission, package, signer and asset checks to pass. The workflow stages the four releases as drafts, verifies size and SHA-256 on every configured destination, promotes only after draft checks, and verifies public URLs and asset digests again.
7. Confirm each release is stable (not a draft or prerelease), each canonical release page contains the expected APK, and both repositories' copies match the recorded ledger in [`docs/published-release-digests.md`](docs/published-release-digests.md).

The four bridge APKs (Live Guard 2.45, Ramesh Radio 1.10, PulseEQ 1.4 and RP Vocal Remover Studio 0.1.10) are published on `universalrp2003/InternetRadio`, because already-installed clients use that endpoint. The clients query `universalrp2003/RP-Android-Apps` first and then `InternetRadio`; they do not display separate per-repository status. The owner confirmed on 14 October that all four bridge apps work and manual update checks finish without errors, so the one-to-two-month grace period is in progress. The target repository is public but has no app releases yet, so new-source release retrieval remains to be validated. Keep `InternetRadio`, its tags and assets intact; do not rename or archive it until the grace period and new-source update checks are complete. See [`docs/repository-migration.md`](docs/repository-migration.md) for the current gate and sequence.

### Live Guard permission gate

The release workflow compares the merged CleanSweep APK manifest with the most recently published Live Guard release. A newly added dangerous permission requires a truthful physical-device evidence record that identifies the exact tested APK bytes. If an evidence record exists, only its matching bytes may be released. Missing or unreadable prior-release data fails closed. App-op/signature permissions are not treated as Android runtime-dangerous permissions.

No release process disables Play Protect or bypasses Android's installer. Users may need to grant the per-app **Install unknown apps** permission in Settings, and Android shows its own confirmation for each installation.

## Store metadata

The active store-art pipeline covers CleanSweep, Ramesh Radio and PulseEQ. F-Droid metadata is present for those three modules; it is preparation, not a claim that F-Droid has accepted or published them. RP Vocal Remover Studio currently ships through its GitHub Release only. See [`store/README.md`](store/README.md).
