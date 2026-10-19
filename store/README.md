# Store metadata and artwork — RP Ideas

The active store-art pipeline covers the three apps with store metadata: Live Guard (`cleaner`), Ramesh Radio (`radio`) and PulseEQ (`equalizer`). RP Vocal Remover Studio and Oor Arivippu are standalone GitHub-release APKs and are not included in the store-art pipeline.

## What is in this directory

- [`PLAY_SUBMISSION.md`](PLAY_SUBMISSION.md) — current artifact and submission-readiness notes; it does not imply that a listing is submitted or approved.
- [`fdroid/README.md`](fdroid/README.md) and [`fdroid/metadata/`](fdroid/metadata/) — F-Droid build recipes for the three current store-art apps. Prepared metadata is not the same as an accepted or published listing.
- [`fdroid/REPORT.md`](fdroid/REPORT.md) — current store-channel status and caveats.
- [`incoming/README.md`](incoming/README.md) — safe handling rules for any future screenshot intake.
- `<module>/fastlane/metadata/android/<locale>/` — listing text, changelogs, icons, feature graphics and available phone screenshots. English listings are present for all three; Tamil listings are present for Live Guard and Ramesh Radio.

## Generated art

The checked-in 512×512 icons and 1024×500 feature graphics are generated from each app's launcher/theme assets and listing text. The current scripts handle only `cleaner`, `radio` and `equalizer`:

```sh
python3 tools/make_store_assets.py icons
bash tools/make_feature_graphics.sh
```

Phone screenshots must come from real app screens. To place sanitized portrait screenshots in the appropriate metadata folder:

```sh
python3 tools/make_screenshots.py cleaner screen1.png screen2.png
python3 tools/make_screenshots.py radio --locale all screen1.png screen2.png
python3 tools/make_screenshots.py equalizer screen1.png screen2.png
```

Use only images approved for public release. Remove or redact personal information, installed-app lists, IP/network details, account identifiers and notifications on the phone before adding screenshots. Do not commit private diagnostic logs or user audio as store assets.

## Build artifacts

The existing batch workflow builds the four legacy apps and AABs for `cleaner`, `radio` and `equalizer`; Oor Arivippu is built by a separate verified release workflow. RP Vocal Remover Studio and Oor Arivippu are distributed as standalone APKs. Exact current APK names and verified hashes are listed in [`../docs/published-release-digests.md`](../docs/published-release-digests.md); the GitHub Release pages remain the authoritative downloads.
