# Build and release guide — RP Ideas

This guide covers the four active modules in the current repository. GitHub Releases are the verified direct-download channel. A successful CI build is required before publication; CI success is not Play Protect approval or a device-compatibility certification.

## Current modules and releases

| App | Gradle module | Current version | Release tag | APK |
|---|---|---:|---|---|
| Live Guard / CleanSweep | `cleaner` | 2.44 | `live-guard-v2.44` | `CleanSweep-v2.44.apk` |
| Ramesh Radio | `radio` | 1.9 | `ramesh-radio-v1.9` | `Ramesh-Radio-v1.9.apk` |
| PulseEQ | `equalizer` | 1.3 | `pulseeq-v1.3` | `PulseEQ-v1.3.apk` |
| RP Vocal Remover Studio | `vocalremover` | 0.1.9 | `rp-vocal-remover-studio-v0.1.9` | `RP-Vocal-Remover-Studio-v0.1.9.apk` |

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
3. Push the verified changes to the authorized branch. For a batch publication, the workflow's existing branch trigger requires `[publish]` in the commit message. A release-tag/dispatch path may be used only when the connected GitHub integration permits it.
4. Wait for all build, regression, permission, package, signer and asset checks to pass. The workflow stages four releases as drafts, verifies each asset's size and digest, publishes them only after all draft checks pass, then verifies each public release URL and asset again.
5. Confirm each release is stable (not a draft or prerelease), each canonical release page contains the expected APK, and each public asset digest matches the recorded ledger in [`docs/published-release-digests.md`](docs/published-release-digests.md).

Expected public release paths use the current repository slug `universalrp2003/InternetRadio`. **Do not change that slug yet.** The installed update clients reject redirects from a repository rename; the staged migration and required bridge-release sequence are in [`docs/repository-migration.md`](docs/repository-migration.md).

### Live Guard permission gate

The release workflow compares the merged CleanSweep APK manifest with the most recently published Live Guard release. A newly added dangerous permission requires a truthful physical-device evidence record that identifies the exact tested APK bytes. If an evidence record exists, only its matching bytes may be released. Missing or unreadable prior-release data fails closed. App-op/signature permissions are not treated as Android runtime-dangerous permissions.

No release process disables Play Protect or bypasses Android's installer. Users may need to grant the per-app **Install unknown apps** permission in Settings, and Android shows its own confirmation for each installation.

## Store metadata

The active store-art pipeline covers CleanSweep, Ramesh Radio and PulseEQ. F-Droid metadata is present for those three modules; it is preparation, not a claim that F-Droid has accepted or published them. RP Vocal Remover Studio currently ships through its GitHub Release only. See [`store/README.md`](store/README.md).
