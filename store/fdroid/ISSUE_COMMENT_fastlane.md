# Reply to F-Droid Bot on fdroid/rfp#4512 — paste this into the issue

F-Droid's bot answered on the issue with "Fastlane was not found in your repo!". **It looked at
the wrong branch**: the report says `@ f6c906f`, which is the tip of `main`, and `main` is 93
commits behind the work — it predates the fastlane folders, the licence file and the store
metadata. Everything the bot asked for exists on the development branch, which is being merged
into `main` now. Paste the comment below into the issue so the maintainers know to re-scan.

--- copy from the next line ---

Thanks for the report. The scan looked at the default branch (`main`, at `f6c906f`) — that
branch is 93 commits behind the work, so it predates the fastlane folders, the licence file and
the store metadata. Everything is present on the development branch and is being merged into
`main` now:

- **Fastlane:** 40 files — `title.txt`, `short_description.txt`, `full_description.txt` and
  `changelogs/<versionCode>.txt` per app, plus `ta-IN` for CleanSweep and Ramesh Radio
- **LICENSE:** GPL-3.0-or-later, full text with copyright notice
- **Icons and feature graphics:** 512x512 `icon.png` and 1024x500 `featureGraphic.png` per app,
  generated from the real launcher vectors
- **Ready fdroiddata recipes:** `store/fdroid/metadata/*.yml`, one per application id, using
  `subdir` to select each module of this one Gradle project
- **Screenshots:** being placed now (they have to come from a real phone, so they are the last
  piece)

I will reply here the moment the default branch carries all of it, so the re-scan sees the
repository as it is meant to be.

--- end of paste ---
