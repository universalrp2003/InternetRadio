# F-Droid metadata and build recipes

This directory contains source-build recipes for the three apps currently prepared for store metadata: Live Guard, Ramesh Radio and PulseEQ. The recipes point to public release tags in `universalrp2003/InternetRadio` and select each Gradle module with `subdir`.

Prepared recipes:

| App | Metadata file | Source module | Current tag |
|---|---|---|---|
| Live Guard (CleanSweep) | `metadata/com.universalrp.cleansweep.yml` | `cleaner` | `live-guard-v2.44` |
| Ramesh Radio | `metadata/com.universalrp.tamilnadufm.yml` | `radio` | `ramesh-radio-v1.9` |
| PulseEQ | `metadata/com.universalrp.pulseeq.yml` | `equalizer` | `pulseeq-v1.3` |

RP Vocal Remover Studio is currently a standalone GitHub-release APK and has no recipe here. Recipe presence means only that metadata is prepared; verify the F-Droid issue/merge-request status before stating that an app is accepted or available.

## Build and signing notes

F-Droid builds source and distributes APKs signed with its signing identity. Those APKs are not normally install-over updates for copies signed with this project's key. Anyone switching an installed direct-download app to a separately signed store build may need to uninstall the former copy first; warn users about potential local-data loss and follow the store's current installation guidance. Do not promise signature continuity between channels.

The recipe's `commit` is the app's current release tag. Keep its `versionName` and `versionCode` aligned with the module's Gradle configuration and the tagged source. Before opening or updating a request, validate the current metadata with F-Droid's current tools and policies; do not assume a prior submission state remains current.

## When preparing a recipe update

1. Build and verify the app using the normal CI release workflow. Never change an already published tag or replace its APK.
2. Confirm the source tag contains the exact Gradle module and version referenced by the recipe.
3. Update the corresponding YAML `Builds` entry and `CurrentVersion` fields together.
4. Review permissions, network behavior, bundled assets, licenses and third-party notices against the exact source revision.
5. Submit the metadata using the current F-Droid contribution process, then record any review outcome in [`REPORT.md`](REPORT.md). Do not describe a submitted request as accepted until the repository listing is live.

The recipe source URL remains on the existing repository until the staged updater migration is complete; see [`../../docs/repository-migration.md`](../../docs/repository-migration.md). The old repository must not be renamed while installed update clients depend on its current API path.
