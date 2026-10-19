# Store submission readiness — current scope

This file is a checklist, not evidence that any app is listed, submitted, accepted or approved by a store. Confirm the current status in each store's console before making availability claims.

## Active apps and artifacts

| App | Module | Current release | Google Play bundle built by CI | F-Droid recipe |
|---|---|---:|---|---|
| Live Guard (CleanSweep) | `cleaner` | 2.46 / code 51 | Yes | `com.universalrp.cleansweep.yml` |
| Ramesh Radio | `radio` | 1.11 / code 11 | Yes | `com.universalrp.tamilnadufm.yml` |
| PulseEQ | `equalizer` | 1.5 / code 6 | Yes | `com.universalrp.pulseeq.yml` |
| RP Vocal Remover Studio | `vocalremover` | 0.1.11 / code 12 | No; direct APK distribution | No recipe in this store-art pipeline |

All four versions passed build and release verification in [run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) and have public stable APK releases on both GitHub repositories. CI also built AABs for the three store-packaged apps. Release publication and AAB generation do not mean an app was submitted to, accepted by, or published in Google Play; use the exact matching run artifact and current store-console requirements for any future submission.

All four have stable direct-download APKs in GitHub Releases. An AAB produced by CI, fastlane metadata, or an F-Droid recipe does not establish store acceptance or availability. See [`../PUBLISHING.md`](../PUBLISHING.md) and [`../docs/published-release-digests.md`](../docs/published-release-digests.md).

## Before a Google Play submission

1. Confirm that a Play Console app exists for the exact package ID and that the owner has selected the intended app name, countries, pricing and track.
2. Download the AAB generated from the same verified source/release run. Do not upload debug builds or substitute an APK for the bundle.
3. Use current listing text and graphics under the relevant module's `fastlane/metadata/android/` directory. Current store art is maintained for `cleaner`, `radio` and `equalizer`; RP Vocal Remover Studio is not currently prepared for Play submission.
4. Use the current public privacy policy: <https://github.com/universalrp2003/InternetRadio/blob/main/PRIVACY.md>. Reconfirm the URL loads the current policy after this branch is merged.
5. Complete the target audience, content rating, data-safety, permission and other Play Console declarations from the app's actual behavior and the current Google Play requirements. Do not copy generic answers from an old checklist.
6. Live Guard uses Android all-files access for its file-review/cleanup functionality. Review the current Play policy and required declaration carefully before submitting; a CI AAB does not establish permission eligibility or store approval.
7. Keep release notes aligned with the exact version code and bytes submitted. Record the submission/review outcome separately from CI results.

## Screenshots and privacy

Screenshots must be genuine app captures. Review them for personal files, installed-app lists, account names, public/private IP addresses, router details, SIM/operator information and notifications. Sanitize on the device before placing the screenshots in the public repository. Never add selected audio, private logs, API keys or user-identifying data to store metadata.

For current app behavior and third-party data flows, see [`../PRIVACY.md`](../PRIVACY.md). For F-Droid recipe scope and signing caveats, see [`fdroid/README.md`](fdroid/README.md) and [`fdroid/REPORT.md`](fdroid/REPORT.md).
