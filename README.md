# CleanSweep · Ramesh Radio · PulseEQ

Three actively maintained, GPL-3.0 Android apps by **Ramesh prathap .R**.
Designed for **Android 8.0 (API 26) and newer across manufacturers**, not just
one phone model. Some audio effects and background behaviour depend on Android
and the device; not every model has been tested.

| App | Module | Purpose |
|---|---|---|
| **Live Guard (CleanSweep)** | `cleaner` | Phone cleaning, health/security review and optional AI assistance. |
| **Ramesh Radio** | `radio` | Tamil/world radio, folder-based local music, stereo balance and EQ. |
| **PulseEQ** | `equalizer` | Local audio DSP and device-dependent external-session equalization. |

AppForge (`builder`) and the original Internet Radio (`app`) are preserved as
legacy projects, but are not part of this release or the current development focus.

## Download — release APKs

| App | Version | Installable APK |
|---|---|---|
| Live Guard (CleanSweep) | 2.34 | [CleanSweep-v2.34.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.10-8/CleanSweep-v2.34.apk) |
| Ramesh Radio | 1.8 | [Ramesh-Radio-v1.8.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.10-8/Ramesh-Radio-v1.8.apk) |
| PulseEQ | 1.2 | [PulseEQ-v1.2.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.10-8/PulseEQ-v1.2.apk) |

[Release notes and all assets](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.10-8)
· [All releases](https://github.com/universalrp2003/InternetRadio/releases)

Install **one release APK per app**. Debug APKs are development builds; you do not
need both. AABs are for store submissions and cannot be installed directly.
GitHub publication does not mean the apps have been published to Google Play or F-Droid.
CI artifacts are temporary build downloads, not the public release channel.

## What's new

- **Live Guard / CleanSweep 2.34:** watts-only status pill while a charger is connected;
  normal data/network view restored on unplug with background monitoring enabled;
  app-specific security Fix/Manage with manufacturer-aware fallbacks; and a fresh,
  complete security AI request that honours app-name/network sharing switches.
  Includes 27 regression tests. Charging/display behaviour was user-confirmed on
  **Redmi 13 5G** after publication; other models and the full feature matrix remain
  unverified. See the [device evidence and test plan](docs/live-guard-2.34-device-test-plan.md).
- **Ramesh Radio 1.8:** retains current station-discovery and playback improvements;
  unchanged in this bug-fix release. **PulseEQ 1.2** is also unchanged.

Earlier highlights:

- **CleanSweep 2.14:** accurate battery-side wattage & honest discharging display on widget with timestamp, optional persistent background monitoring mode with Stop button, Tamil voice alerts, compact header, attributed Tamil news and cybersecurity alerts strip, normalized Gemini model readiness check without spurious errors, and greeting lookup bypass.
- **CleanSweep 2.13:** evidence-based officeholder replies, dated news RSS, raw-JSON
  suppression, keyboard fixes and clearer phone-analysis guidance.
- **Ramesh Radio 1.7:** single-file picker reconnection fix, persistent folder queues,
  local transport controls, stereo balance, embedded external-session EQ and stream recovery.
- **PulseEQ 1.2:** version-aware About and GitHub update checks with release notes.
- All three offer manual update checks and optional daily-on-launch checks for
  **published stable GitHub releases**. Nothing installs automatically.

**Not included:** AI vocal separation/karaoke. System-wide EQ is not guaranteed on
all devices. Phone AI advice can be wrong; news headlines are attributed feed items,
not independently verified reporting.

See [full release notes](releases/v2026.10.10-8.md), [privacy](PRIVACY.md),
[Radio device test plan](docs/radio-1.6-device-test-plan.md), and
[publishing guidance](PUBLISHING.md).

## Licence

**GNU GPL-3.0** — Copyright (C) 2026 Ramesh prathap .R (`universalrp2003@gmail.com`).
The full text is in [LICENSE](LICENSE); the apps show the notice in **About**. See
[PRIVACY.md](PRIVACY.md) for what the apps do and do not do with your data, and
[store/PLAY_SUBMISSION.md](store/PLAY_SUBMISSION.md) for the Google Play upload pack.

## Building

- **Cloud (easiest):** push to `main`, push an `arena/**` branch, or use *Actions → Build APK → Run workflow*.
  Download the `internet-radio-apk`, `cleansweep-apk`, `appforge-apk`, `pulseeq-apk` and `ramesh-radio-apk` artifacts.
- **Local:** open the repo root in Android Studio, or run
  `gradle wrapper && ./gradlew assembleDebug`
- **Publishing:** every store route (F-Droid, IzzyOnDroid, Google Play, your own F-Droid
  repo, Obtainium) and what each one needs is in [PUBLISHING.md](PUBLISHING.md). Version
  tags now publish a GitHub Release with the version-named APKs attached..

## Station list refresh (Ramesh Radio)

The app ships a station list inside the APK, and that list is rebuilt from the open
Radio-Browser directory by `.github/workflows/refresh-stations.yml`:

- monthly, on demand, or whenever `tools/fetch_stations.py` / `tools/curated_stations.json` change;
- every hand-picked stream in `tools/curated_stations.json` is **probed** first, and only the
  ones that really answer with audio (or a valid HLS playlist) are shipped;
- the result is committed to `radio/src/main/assets/stations_seed.json` and packaged into the
  next APK, so a build never ships a station link that was already dead at build time.

## Earlier history

- **CleanSweep v2.6** — the "repair everything" round, from the phone's own bug report:
  the **scan no longer crashes or hangs** (the widget refresh is out of the scan loop, the
  engine keeps counts instead of every file name, an out-of-memory scan is caught and
  explained, and the scanner screen offers a **Start the scan** button if a scan was never
  actually running), the **"Run the daily check now" button works at any hour** (it used to
  return silently before the evening brief time), the **voice screen is readable** (one
  broken card was drawing every Tamil label on top of the others), the **Wi-Fi network name
  is never shown or sent**, **plugging in the charger now really works in the background**
  (a receiver may not start a foreground service on Android 12+, so a job does it, shows the
  charging card with the real watts and speaks the details), the **AI can answer live
  questions** — a Google key now searches the web and shows its sources instead of refusing
  "today's latest news" — **jitter is labelled next to ping**, the **downloaded APK carries
  the version in its file name** (`CleanSweep-v2.6.apk`), and the voice can use the phone's
  **natural online voice** instead of the robotic offline one, with the offline voice as the
  fallback. The last crash is recorded on the phone and shown in **About**, so the next bug
  report can name the real cause.
- **Ramesh Radio v1.2** — the language menu now reads **தமிழ்** and it is the **first and
  default selection**, so the app opens on Tamil instead of "All". Nothing else in the radio
  changed.

- **CleanSweep v2.5** — a **home-screen widget** (battery, watts, free storage, the last brief,
  plus **Clean** and **Read brief** buttons), a **separate AI answer language** (auto / English
  / Tamil, independent of the menu), and five more spoken warnings — storage below 1 GB,
  charger pulled out early, **charger connected but not charging**, battery health worn, and
  **a new device on your Wi-Fi** (Wi-Fi ⇄ mobile-data changes are available too, off by
  default). Every one has its own switch and respects quiet hours.
- **CleanSweep v2.4** — the app now **speaks**: battery low, battery full, "running hot",
  charging started and a **daily brief** are announced out loud, each behind its own switch,
  with **quiet hours** (22:00–7:00 by default) so nothing talks while you sleep. The assistant
  takes **dictation** through the phone's own recogniser and can **read any answer aloud**, and
  it now answers **any question**, not only cleaning topics. A **daily full check** reads
  battery, temperature, storage, junk and security, asks your AI for a two-sentence summary,
  speaks it and posts the full report as a notification. Plugging in the charger starts the
  **charging details and the spoken line by itself**. Fixes: the **ping/jitter test** was
  measuring nothing (sockets on the UI thread) and now works, the **public IP** check survives
  VPN-blocked Cloudflare by trying four providers, and "mobile data looks OFF" no longer shows
  when data is on. Plus a one-tap **த/EN** language button on Home.
- **CleanSweep v2.3** — speaks Tamil: one switch turns the whole menu into Tamil and renames
  the app to சுத்தம் செய்பவர் — “the cleaner” (the launcher label follows a Tamil phone too). The About screen
  now credits the author, ரமேஷ் பிரதாப் / Ramesh prathap .R, with a tappable
  universalrp2003@gmail.com and github.com/universalrp2003. The status-bar watt reading can be
  moved anywhere — arrow pad, or **Drag** to slide it with your finger — and the spot is
  remembered, because every phone fills that strip with VoLTE, VPN or the carrier name.
  The speed test now **asks for its size** (your last choice is remembered) instead of
  assuming a download you did not ask for. AI keys and model choices are now saved **per provider**, so switching between
  Gemini, Groq and the free option never asks for the same key twice; busy providers
  (HTTP 502/503/504) and slow answers are retried automatically and explained in plain words.
  And the new **Mobile & data** screen covers the mobile side: operator and SIM names, 5G/4G,
  the real signal in dBm, cell towers, public IP and ISP, ping **with jitter**, a real speed
  test with 1–100 MB sizes (with a data-usage warning, and 100 MB for unlimited 5G), and
  today's mobile and Wi-Fi data usage with the apps that used the most.
- **CleanSweep v2.2** — the empty part of the status bar now carries a tiny live watt reading
  beside the front camera (the ongoing charging card with time-to-full is still there), the
  system back button walks back through the screens instead of closing the app, each quick
  cleaning tile scans only its own kind of junk, nothing is pre-ticked after a scan (with
  per-category preselect switches in Settings for those who want them), and the app checks on
  every launch that the chosen AI can really answer — naming the provider and model, and
  switching to a free AI when it cannot.
- **CleanSweep v2.1** — the status bar now shows charging watts and the time to full while
  you charge, the AI settings load the real model list from your provider (no more guessing
  a model name), the analysis screen shows green/amber/red vitals side by side with the AI's
  explanation, and Wi-Fi devices are identified as phone / laptop / camera / printer / TV /
  NAS with the evidence shown.
- **CleanSweep v2.0** — becomes a full phone-care app for every Android phone, not just Redmi: battery,
  temperature, voltage, charging current and watt readings; an installed-app manager that flags bloatware,
  unused and sideloaded apps; a security review; a Wi-Fi scanner that counts the devices on your network;
  and optional AI analysis with your own key. It also fixes the bug where cleaning reported "0 MB cleaned"
  on Android 9 phones such as the Oppo A3s (missing runtime storage permission). Still no Accessibility
  service and still fully offline except the AI analysis you start yourself.
- **AppForge v1.0** — a new offline app builder module (`builder/`) whose exports are real, working
  apps (HTML) and real, buildable Android projects (Kotlin + Gradle + CI workflow).
- **Ramesh Radio v1.1** — the radio + news + local-player module (`radio/`, formerly *Tamilnadu FM
  Radio*) with a 10-band equalizer, Clear sound processing, a **home-screen widget**, a **"TN towns"
  chip** for Tamil Nadu city FM, and a station list that refreshes itself from the open directory
  (`.github/workflows/refresh-stations.yml`). Built by **Ramesh prathap .R**.
- **PulseEQ v1.0** — a new 20-band equalizer module (`equalizer/`) with real DSP of its own, LED
  spectrum animation driven by the audio, a guaranteed 20-band built-in player, honest system-wide
  support reporting, and a foreground service so it keeps working in the background.
