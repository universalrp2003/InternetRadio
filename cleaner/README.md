# CleanSweep — Junk & Cache Cleaner for Android

A privacy-friendly cleaning app built for **Redmi 13 5G** (HyperOS / MIUI, Android 14)
and any Android 8+ phone (MIUI/HyperOS, One UI, Pixel, OnePlus, Realme, Vivo…).
100% offline — it does not even request the INTERNET permission.

**v1.2 highlights:** HyperOS auto-clean now taps *Clear data → Clear cache → OK*
(confirmation dialog included); animated LED light bars + optional sound effects;
CI signs every build with one fixed keystore so APKs install as **updates**.

## What it cleans

| Category | What it finds | Selected by default |
|---|---|---|
| Residual & temp files | `.tmp`, `.log`, `.bak`, partial downloads, `Thumbs.db`, Office `~$` files | ✅ |
| Thumbnail cache | hidden `.thumbnails` image caches (gallery rebuilds them) | ✅ |
| Duplicate files | identical files (size + hash); **newest copy is always kept** | ✅ |
| APK installer files | downloaded installers, with installed/not-installed detection | ✅ |
| Empty folders | empty folders, incl. folders that become empty after cleaning | ✅ |
| Old downloads | files in `Download/` older than N days (configurable) | ❌ |
| Large files | files above a threshold (configurable) for manual review | ❌ |

Plus an **App cache cleaner**: shows per-app cache sizes (StorageStats API) and clears
them either **automatically** via an optional Accessibility service (it opens each app's
settings page and taps *Storage → Clear cache*, including the MIUI/HyperOS *Clear data*
sheet), or **manually** with guided steps.

## Features

- Material 3 dark UI with a live storage gauge and radar-style scan animation
- One-tap clean with per-file / per-category selection and confirmation
- Settings: hidden-folder scan, duplicate min size, large-file threshold,
  old-download age, protected folders list
- MediaStore cleanup after deletion so gallery/file managers update instantly
- No ads, no analytics, no accounts, no INTERNET permission

## Building the APK

### Option A — GitHub Actions (no Android SDK needed)
Push to `main` (or run **Actions → Build APK → Run workflow**). Download the
`cleansweep-apk` artifact.

### Option B — Android Studio
Open this repository root in Android Studio and run the `cleaner` run configuration
(or *Build → Build APK(s)* with the `cleaner` module selected).

### Option C — Command line
```bash
gradle wrapper            # once, if the wrapper is missing
./gradlew :cleaner:assembleDebug
# APK at cleaner/build/outputs/apk/debug/cleaner-debug.apk
```

## Installing on the Redmi 13 5G

1. Copy `cleaner-debug.apk` to the phone and open it (allow "install unknown apps").
2. Launch CleanSweep and tap **Allow storage access** → switch CleanSweep ON.
3. Tap **Scan & clean junk**. Review the selection, then **Clean**.
4. For app caches: **App cache** → grant *Usage access* → (recommended) enable
   *Auto clean (Accessibility)* → **Clean cache**.

## Honest limitations (Android security)

- Apps can't silently wipe other apps' caches without root; the Accessibility
  automation (or guided manual mode) is the standard workaround.
  On MIUI/HyperOS it taps the bottom-bar **Clear data** button and then
  **Clear cache** in the sheet; on stock Android/One UI it goes through the
  **Storage** page; progress toasts show what it's doing.
- `Android/data` and `Android/obb` are locked by the system since Android 11,
  even for cleaners. Redmi's built-in *Security → Cleaner* can reach some of them.
- The Accessibility service is strictly optional, only taps "Clear cache" buttons,
  and never reads or stores your content.
