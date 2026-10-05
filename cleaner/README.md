# CleanSweep — Junk & Cache Cleaner for Android

A privacy-friendly cleaning app built for **Redmi 13 5G** (HyperOS / MIUI, Android 14)
and any Android 8+ phone (MIUI/HyperOS, One UI, Pixel, OnePlus, Realme, Vivo…).
100% offline — it does not even request the INTERNET permission.

**v1.3 highlights:** the Accessibility service is **gone**. CleanSweep never asks for the
Accessibility permission, never reads your screen and never taps inside other apps.
App caches are cleared with a **guided two-tap flow** instead, and a new **on-device
assistant** explains your storage and answers cleaning questions offline.

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

## App cache cleaning (no Accessibility)

Android does not allow one app to wipe another app's cache — that needs root, and the
old workaround (an Accessibility service that taps buttons for you) asks for a very
powerful permission. v1.3 removed it completely and replaced it with a guided flow:

1. **App cache** shows per-app cache sizes (Storage API + Usage access).
2. Tick the apps you want (sorted by cache size) and tap **Clean cache**.
3. CleanSweep opens the first app's storage page — you tap **Clear cache**.
4. Come back and tap **Next app** to continue down the list.

Two taps per app, you always see what is happening, and nothing is automated behind your back.

## On-device assistant (new in 1.3)

Tap **Ask the assistant** on the home screen and ask anything about your storage:

- *"What is taking the most space?"* → real numbers from your last scan
- *"What is safe to delete?"* → category-by-category guidance
- *"How do I clear the Instagram cache?"* → exact two-tap steps
- *"Is my data private?"*, *"Why can't you clean Android/data?"*, *"Does cleaning speed up my phone?"*
  → straight, honest answers
- Plus context-aware quick-reply chips, and one-tap actions like *Scan now* or *Open app cache*

It is a small **offline rule engine**, not a cloud chatbot: your question is matched
against built-in rules on the phone, using only the numbers already on screen. There is
no network call, no API key, no model download, and no data collection of any kind.
(Turn off *Detailed answers* in Settings for one-line replies.)

## Features

- Material 3 dark UI with a live storage gauge, radar-style scan animation and LED bars
- One-tap clean with per-file / per-category selection and confirmation
- Settings: hidden-folder scan, duplicate min size, large-file threshold,
  old-download age, protected folders list, sound effects, assistant detail level
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
4. For app caches: **App cache** → grant *Usage access* → tick apps → **Clean cache** →
   tap **Clear cache** on each app's page, returning to tap **Next app**.

## Honest limitations (Android security)

- Apps can't silently wipe other apps' caches without root. CleanSweep v1.3 refuses to
  fake it with Accessibility, so it guides you through the official Settings buttons
  instead — two taps per app.
- `Android/data` and `Android/obb` are locked by the system since Android 11, even for
  cleaners. Redmi's built-in *Security → Cleaner* can reach some of them.
- Cleaning junk frees real disk space; it does not "boost RAM" or "cool down" the phone,
  and CleanSweep will never pretend otherwise (ask the assistant — it says the same thing).
