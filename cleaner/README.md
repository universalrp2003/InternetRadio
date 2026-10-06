# CleanSweep — cleaner, phone health, security & network

A privacy-friendly app for **every Android 8+ phone** — Redmi and other Xiaomi phones
(HyperOS / MIUI), Samsung One UI, Oppo, Vivo, Realme, OnePlus, Motorola, Nokia, Tecno
and stock Android. Tested on a Redmi 13 5G and an Oppo A3s.

**v2.2 highlights**

- **Charging watts beside the clock** — the user pointed at the empty part of the status bar
  and asked for the watt reading there. Android has no API for a third-party status-bar
  item, so CleanSweep draws its own tiny `⚡ 3.9 W` reading in that gap (it even steps around
  the front-camera cutout). It is off by default, needs "Display over other apps", only
  appears while charging, and cannot be tapped. The ongoing charging card stays too.
- **The back button works** — from any screen the system back gesture now walks back through
  the screens you opened instead of closing the whole app; only Home lets Android exit.
- **Quick cleaning does exactly what it says** — the tiles now scan *only* their own
  category: Temp & junk, Thumbnails, APK files, Duplicates, Empty folders, Old downloads,
  Large files. No more full scan for a duplicate hunt.
- **You choose what to clean** — nothing is ticked after a scan any more. Every list starts
  empty with "Tick what you want removed"; **Select all / Clear** is one tap away, and
  Settings → **Preselect after a scan** is where you opt into automatic ticking per category
  (Thumbnail cache, Temp files, APK files, Duplicates…).
- **The AI is checked every time the app opens** — the Home screen shows a green "AI ready"
  card with the live engine (`Gemini · gemini-2.5-flash`). If the provider does not answer,
  it says why in plain words and offers **Use a free AI**, which switches to the keyless
  lane and remembers the endpoint that answered.
- **Every answer says who produced it** — the assistant bubble, the AI report and the device
  identification all print the provider, the model and how long it took. If your provider
  fails mid-question, a free AI answers instead and the app says so.
- **A saved key is used automatically** — paste any key and CleanSweep recognises the
  provider from its prefix (AIza… = Gemini, sk-or-… = OpenRouter, gsk_… = Groq, nvapi-… =
  NVIDIA), fills in a current model and switches the assistant to Online AI in one step.
- **Wi-Fi permission fixed for real** — the card now only appears when the details are
  genuinely unreadable, "Allow and rescan" re-reads the network the moment the dialog
  closes, the Wi-Fi name is found even while a VPN is running, and Android 13+ is happy with
  Nearby-devices alone (Location is no longer demanded).
- **Markdown is rendered** — AI answers no longer show raw `**asterisks**`; bullets, bold
  runs and headings are drawn properly.

**v2.1 highlights**

- **Charging card in the status bar** — while the charger is connected an ongoing
  notification shows the real charging power in watts, the current going in, battery
  temperature, the percentage and an estimate of the time left to full (computed from the
  actual current, and it explains why the last 20% takes longest).
- **AI analysis, much easier**: a **"Load models"** button asks the provider which models
  your key can use (that is what fixes the "model has reached its end of life" 410 error
  on NVIDIA and friends), plus suggested model chips for every provider. The Free option
  now tries Kilo's gateway first (about 200 free requests an hour, no key) before the
  community endpoints.
- **AI analysis, much clearer**: the report screen opens with side-by-side vital boxes
  (battery, battery heat, CPU heat, watts, RAM, storage, security score, time to
  full/empty) coloured green/amber/red with plain-language notes, and the AI's answer is
  split into sections with traffic-light dots instead of one long wall of text.
- **Network devices explained**: each device is now identified as phone / laptop / Windows
  PC / iPhone / IP camera / printer / TV / NAS / smart-home gear with the evidence shown
  ("answers on 554 (RTSP video)"), and an **Identify with AI** button gives a second
  opinion on anything unclear.
- **Fixed**: the Wi-Fi permission card no longer keeps coming back after you allow it, the
  refresh button gives visible feedback, and the device list no longer hides under the
  3-button navigation bar (bottom insets everywhere).
- **Fixed**: the duplicated "Detailed assistant answers" row in Settings is gone and
  replaced by an **On-device / Online AI** choice for the assistant, plus a switch for the
  charging notification.
- **Fixed**: "Fastest core now" can no longer look higher than "maximum" (both are measured
  across all cores), and the hottest-sensor reading explains itself.

**v2.0 highlights**

- **Battery & hardware monitor** — charge, temperature, voltage, **charging/discharging
  current**, **watts**, battery health, charge counter, **CPU temperature**, CPU
  frequency/load, RAM and storage. Everything the phone itself reports; missing values
  are labelled "not reported" instead of being guessed.
- **Fix: "0 MB cleaned" on Android 8/9/10.** Cleaning needs runtime
  `WRITE_EXTERNAL_STORAGE` on those versions, which v1.3 never asked for — so the scan
  found junk, deleted nothing, and still showed a success message. v2.0 requests the
  permission, refuses to scan without it, and when a delete really fails it says
  **"Nothing was deleted"** with the reason and a button to fix the permission.
- **Installed apps** — sizes, last-used dates, installer, permissions; tags for
  preinstalled, unused 30+ days, sideloaded, sensitive-permission and known bloatware
  stubs. Tap an app for details, **uninstall** or open its settings.
- **Security review** — accessibility services, notification readers, device admins,
  "install unknown apps", overlay permission, SMS readers, sideloaded apps, screen lock
  and patch age, with a 0–100 score and a fix hint for every finding.
- **Wi-Fi & network** — your Wi-Fi name, IP, gateway, DNS, signal, band and link speed,
  plus a **device scan that shows how many devices answered on your network** (phones,
  computers, routers, smart devices) using a TCP probe sweep + the ARP table.
- **AI analysis (optional)** — Google Gemini, NVIDIA NIM, OpenRouter, Groq, OpenAI, any
  custom OpenAI-style endpoint (including a model on your own computer), or a **free
  keyless option** that needs no signup. You paste your own key; the report goes from
  your phone straight to that provider, and you can see exactly what was sent.
- **Still no Accessibility service, ever.**

**v1.3 (kept):** the Accessibility service was removed; app caches are cleared with a
guided two-tap flow, and an **on-device assistant** explains your storage offline.

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

## Installing (any phone)

1. Copy `cleaner-debug.apk` to the phone and open it (allow "install unknown apps").
2. Launch CleanSweep and tap **Allow storage access** → switch CleanSweep ON.
   On Android 8/9/10 CleanSweep also asks for the storage permission at first launch —
   without it no cleaner can delete anything on those versions.
3. Tap **Scan & clean junk**. Review the selection, then **Clean**.
4. For app caches: **App cache** → grant *Usage access* → tick apps → **Clean cache** →
   tap **Clear cache** on each app's page, returning to tap **Next app**.
5. **Phone health** for battery/temperature/watts; **Security check**; **Wi-Fi devices**;
   **Installed apps**; and **AI analysis** (optionally with your own key in Settings).

## Honest limitations (Android security)

- Apps can't silently wipe other apps' caches without root. CleanSweep v1.3 refuses to
  fake it with Accessibility, so it guides you through the official Settings buttons
  instead — two taps per app.
- `Android/data` and `Android/obb` are locked by the system since Android 11, even for
  cleaners. Redmi's built-in *Security → Cleaner* can reach some of them.
- Cleaning junk frees real disk space; it does not "boost RAM" and it cannot cool a phone
  down. It *measures* heat honestly (CPU/battery sensors) and explains what is normal.
- The security review is a **permissions and settings review**, not an antivirus: no file
  hashes are checked anywhere. A phone can have malware that looks like a normal app.
  Play Protect (and an online scanner) covers that part.
- The Wi-Fi device list can only show devices that answer. Sleeping phones, devices that
  block probes, and routers with "AP isolation" will not appear — the screen says so.
- A few phone makers hide the battery **current** counter from apps, and some kernels do
  not expose a CPU temperature sensor. CleanSweep shows "not reported" rather than a
  made-up number.
- The optional AI analysis is the only feature that uses the internet. Without a key it
  uses public keyless endpoints (shared, rate-limited); with a key it uses your own
  quota. Nothing is ever sent unless you tap **Analyse**.
