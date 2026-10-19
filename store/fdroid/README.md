# F-Droid metadata and build recipes

This directory contains source-build recipes for the three apps currently prepared for store metadata: Live Guard, Ramesh Radio and PulseEQ. The recipes point to public release tags in `universalrp2003/InternetRadio` and select each Gradle module with `subdir`.

Prepared recipes:

| App | Metadata file | Source module | Last recipe tag |
|---|---|---|---|
| Live Guard (CleanSweep) | `metadata/com.universalrp.cleansweep.yml` | `cleaner` | `live-guard-v2.44` |
| Ramesh Radio | `metadata/com.universalrp.tamilnadufm.yml` | `radio` | `ramesh-radio-v1.9` |
| PulseEQ | `metadata/com.universalrp.pulseeq.yml` | `equalizer` | `pulseeq-v1.3` |

These prepared recipes still reference the preceding versions (2.44, 1.9 and 1.3), not the 14 October bridge releases. F-Droid recipe metadata has not yet been advanced for those releases. The current GitHub APK versions and published hashes are recorded in [`../../docs/published-release-digests.md`](../../docs/published-release-digests.md). RP Vocal Remover Studio is currently a standalone GitHub-release APK and has no recipe here. Recipe presence means only that metadata is prepared; verify the F-Droid issue/merge-request status before stating that an app is accepted or available.

## Build and signing notes

F-Droid builds source and distributes APKs signed with its signing identity. Those APKs are not normally install-over updates for copies signed with this project's key. Anyone switching an installed direct-download app to a separately signed store build may need to uninstall the former copy first; warn users about potential local-data loss and follow the store's current installation guidance. Do not promise signature continuity between channels.

The recipe's `commit` is the source tag for the version declared by that recipe; it can lag the latest GitHub release. When preparing a recipe update, keep its `versionName` and `versionCode` aligned with the module's Gradle configuration and tagged source. Before opening or updating a request, validate the metadata with F-Droid's current tools and policies; do not assume a prior submission state remains current.

## When preparing a recipe update

1. Build and verify the app using the normal CI release workflow. Never change an already published tag or replace its APK.
2. Confirm the source tag contains the exact Gradle module and version referenced by the recipe.
3. Update the corresponding YAML `Builds` entry and `CurrentVersion` fields together.
4. Review permissions, network behavior, bundled assets, licenses and third-party notices against the exact source revision.
5. Submit the metadata using the current F-Droid contribution process, then record any review outcome in [`REPORT.md`](REPORT.md). Do not describe a submitted request as accepted until the repository listing is live.

The recipe source URL remains on the existing repository until the staged updater migration is complete; see [`../../docs/repository-migration.md`](../../docs/repository-migration.md). The old repository must not be renamed while installed update clients depend on its current API path.
