# InternetRadio workspace

Five Android applications in one Gradle project:

| Module | App | Description |
|---|---|---|
| `:app` | **Internet Radio** | Streams internet radio stations with Media3/ExoPlayer, background playback and a media notification. |
| `:cleaner` | **CleanSweep v2.3** | Junk & cache cleaner for **every Android 8+ phone** (Redmi/HyperOS, Samsung, Oppo, Vivo, Realme, OnePlus, Pixel…). Scans junk, thumbnails, duplicates, leftover APKs and empty folders and clears app caches with a **guided two-tap flow** — **no Accessibility service at all**. v2.0 adds a **battery & hardware monitor** (charge, temperature, voltage, current, **watts**, CPU temp/load, RAM), an **installed-app list** that flags bloatware, unused and sideloaded apps, a **security review** of permissions and settings, a **Wi-Fi scanner** that shows how many devices are on your network, and optional **AI analysis** (Gemini / NVIDIA / OpenRouter / Groq / OpenAI / any custom endpoint / free keyless option) with your own key. v2.1 adds a **charging card in the status bar** (watts, current, temperature, time to full), **"Load models"** so the AI settings list the models your key can really use, a **green/amber/red vitals board** beside the AI analysis, and **device identification** (phone / laptop / camera / printer / TV / NAS) on the Wi-Fi screen. v2.2 adds the **watt reading in the empty part of the status bar** (drawn beside the front-camera cutout), a back button that **walks back through the screens** instead of closing the app, **quick tiles that scan only their own category**, **nothing ticked until you tick it** (with per-category preselect switches in Settings), and an **AI availability check on every launch** that names the live model and switches to a free AI when your provider does not answer. v2.3 adds a **Tamil menu** (the app becomes **சுத்தம் செய்பவர்** (“the cleaner”) when Tamil is chosen, with a Tamil launcher label and Android 13+ per-app language support), the author credit (**ரமேஷ் பிரதாப்** / Ramesh prathap .R / universalrp2003@gmail.com), a **movable** status-bar watt reading (arrow pad, position remembered), per-provider **saved AI keys and models** so switching provider never asks again, automatic **retries with plain-language 502/503/504 and timeout messages**, and a new **Mobile & data** screen (operator and SIM, 5G/4G, real dBm signal, cell towers, public IP/ISP, ping **and jitter**, a **speed test with 1–100 MB sizes** and a data warning, plus today\u2019s **mobile/Wi-Fi data usage** and top data-hungry apps). Fixes the Android 9 "0 MB cleaned" storage-permission bug. See [cleaner/README.md](cleaner/README.md). |
| `:builder` | **AppForge v1.0** | Build an app on your phone: design screens from blocks, preview the real thing as a working offline mini-app, then export a single HTML app or a complete Android Studio project (with its own GitHub Actions workflow) that builds into an APK. See [builder/README.md](builder/README.md). |
| `:radio` | **Ramesh Radio v1.1** | Tamil FM from Tamil Nadu, Sri Lanka, Malaysia, Singapore and the Tamil diaspora, plus Tamil and English **news** radio, a **local audio file player**, favourites/recently-played, a **"TN towns" chip** for city-level FM (Madurai, Puducherry, Kodaikanal, Coimbatore, Tirunelveli …), a sleep timer, a **home-screen widget** (play/pause, next/previous) and a **10-band equalizer (31 Hz – 16 kHz)** with Clear sound (multiband levelling + limiter), rumble cut and hiss cut. Station lists come from the shipped seed plus live search of the open Radio-Browser directory. See [radio/README.md](radio/README.md). |
| `:equalizer` | **PulseEQ v1.0** | 20-band sound equalizer with its own DSP (peaking biquads + preamp + soft limiter), presets for music/movies/speech/podcast/gaming and more, LED light-bar spectrum driven by real audio energy, a built-in 20-band player, cooperating-player session support, and a foreground service that keeps it running in the background. No INTERNET permission. See [equalizer/README.md](equalizer/README.md). |

## Download (no build needed)

Every release is a GitHub Release with a permanent link — nothing expires, and the file names
carry the version:

| App | Version | Download |
|---|---|---|
| **CleanSweep** | 2.6 | [CleanSweep-v2.6.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.06/CleanSweep-v2.6.apk) |
| **Ramesh Radio** | 1.2 | [Ramesh-Radio-v1.2.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.06/Ramesh-Radio-v1.2.apk) |
| **Internet Radio** | 1.0 | [Internet-Radio-v1.0.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.06/Internet-Radio-v1.0.apk) |
| **AppForge** | 1.0 | [AppForge-v1.0.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.06/AppForge-v1.0.apk) |
| **PulseEQ** | 1.0 | [PulseEQ-v1.0.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.06/PulseEQ-v1.0.apk) |

All releases: <https://github.com/universalrp2003/InternetRadio/releases> · These are the
**release** builds (signed with the project keystore, not debuggable), which is what an app
store will take. The "debug" builds CI also produces are for quick testing only.

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

## What's new

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
