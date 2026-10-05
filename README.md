# InternetRadio workspace

Two Android applications in one Gradle project:

| Module | App | Description |
|---|---|---|
| `:app` | **Internet Radio** | Streams internet radio stations with Media3/ExoPlayer, background playback and a media notification. |
| `:cleaner` | **CleanSweep** | Junk & cache cleaner built for Redmi 13 5G (HyperOS/MIUI). Scans and removes junk/temp files, thumbnail caches, duplicate files, leftover APKs and empty folders; clears app caches with guided or automated (Accessibility) cleaning. Fully offline — no INTERNET permission. See [cleaner/README.md](cleaner/README.md). |

## Building

- **Cloud (easiest):** push to `main` (or use *Actions → Build APK → Run workflow*).
  Download the `internet-radio-apk` / `cleansweep-apk` artifacts.
- **Local:** open the repo root in Android Studio, or run
  `gradle wrapper && ./gradlew assembleDebug`.
