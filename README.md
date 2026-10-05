# InternetRadio workspace

Three Android applications in one Gradle project:

| Module | App | Description |
|---|---|---|
| `:app` | **Internet Radio** | Streams internet radio stations with Media3/ExoPlayer, background playback and a media notification. |
| `:cleaner` | **CleanSweep v1.3** | Junk & cache cleaner for Redmi/HyperOS and any Android 8+ phone. Scans junk, thumbnails, duplicates, leftover APKs and empty folders, and clears app caches with a **guided two-tap flow** — **no Accessibility service at all** — plus an **on-device assistant** that explains your storage offline. Fully offline: no INTERNET permission. See [cleaner/README.md](cleaner/README.md). |
| `:builder` | **AppForge v1.0** | Build an app on your phone: design screens from blocks, preview the real thing as a working offline mini-app, then export a single HTML app or a complete Android Studio project (with its own GitHub Actions workflow) that builds into an APK. See [builder/README.md](builder/README.md). |

## Building

- **Cloud (easiest):** push to `main`, push an `arena/**` branch, or use *Actions → Build APK → Run workflow*.
  Download the `internet-radio-apk`, `cleansweep-apk` and `appforge-apk` artifacts.
- **Local:** open the repo root in Android Studio, or run
  `gradle wrapper && ./gradlew assembleDebug`.

## What's new

- **CleanSweep v1.3** — the optional Accessibility service was removed: no screen reading, no automated
  taps, no Accessibility permission. App caches are cleared through the official Settings buttons with a
  "Next app" guide, and a new offline assistant answers questions about your storage.
- **AppForge v1.0** — a new offline app builder module (`builder/`) whose exports are real, working
  apps (HTML) and real, buildable Android projects (Kotlin + Gradle + CI workflow).
