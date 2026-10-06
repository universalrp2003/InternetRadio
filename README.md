# InternetRadio workspace

Five Android applications in one Gradle project:

| Module | App | Description |
|---|---|---|
| `:app` | **Internet Radio** | Streams internet radio stations with Media3/ExoPlayer, background playback and a media notification. |
| `:cleaner` | **CleanSweep v2.2** | Junk & cache cleaner for **every Android 8+ phone** (Redmi/HyperOS, Samsung, Oppo, Vivo, Realme, OnePlus, Pixel…). Scans junk, thumbnails, duplicates, leftover APKs and empty folders and clears app caches with a **guided two-tap flow** — **no Accessibility service at all**. v2.0 adds a **battery & hardware monitor** (charge, temperature, voltage, current, **watts**, CPU temp/load, RAM), an **installed-app list** that flags bloatware, unused and sideloaded apps, a **security review** of permissions and settings, a **Wi-Fi scanner** that shows how many devices are on your network, and optional **AI analysis** (Gemini / NVIDIA / OpenRouter / Groq / OpenAI / any custom endpoint / free keyless option) with your own key. v2.1 adds a **charging card in the status bar** (watts, current, temperature, time to full), **"Load models"** so the AI settings list the models your key can really use, a **green/amber/red vitals board** beside the AI analysis, and **device identification** (phone / laptop / camera / printer / TV / NAS) on the Wi-Fi screen. v2.2 adds the **watt reading in the empty part of the status bar** (drawn beside the front-camera cutout), a back button that **walks back through the screens** instead of closing the app, **quick tiles that scan only their own category**, **nothing ticked until you tick it** (with per-category preselect switches in Settings), and an **AI availability check on every launch** that names the live model and switches to a free AI when your provider does not answer. Fixes the Android 9 "0 MB cleaned" storage-permission bug. See [cleaner/README.md](cleaner/README.md). |
| `:builder` | **AppForge v1.0** | Build an app on your phone: design screens from blocks, preview the real thing as a working offline mini-app, then export a single HTML app or a complete Android Studio project (with its own GitHub Actions workflow) that builds into an APK. See [builder/README.md](builder/README.md). |
| `:radio` | **Ramesh Radio v1.1** | Tamil FM from Tamil Nadu, Sri Lanka, Malaysia, Singapore and the Tamil diaspora, plus Tamil and English **news** radio, a **local audio file player**, favourites/recently-played, a **"TN towns" chip** for city-level FM (Madurai, Puducherry, Kodaikanal, Coimbatore, Tirunelveli …), a sleep timer, a **home-screen widget** (play/pause, next/previous) and a **10-band equalizer (31 Hz – 16 kHz)** with Clear sound (multiband levelling + limiter), rumble cut and hiss cut. Station lists come from the shipped seed plus live search of the open Radio-Browser directory. See [radio/README.md](radio/README.md). |
| `:equalizer` | **PulseEQ v1.0** | 20-band sound equalizer with its own DSP (peaking biquads + preamp + soft limiter), presets for music/movies/speech/podcast/gaming and more, LED light-bar spectrum driven by real audio energy, a built-in 20-band player, cooperating-player session support, and a foreground service that keeps it running in the background. No INTERNET permission. See [equalizer/README.md](equalizer/README.md). |

## Building

- **Cloud (easiest):** push to `main`, push an `arena/**` branch, or use *Actions → Build APK → Run workflow*.
  Download the `internet-radio-apk`, `cleansweep-apk`, `appforge-apk`, `pulseeq-apk` and `ramesh-radio-apk` artifacts.
- **Local:** open the repo root in Android Studio, or run
  `gradle wrapper && ./gradlew assembleDebug`.

## Station list refresh (Ramesh Radio)

The app ships a station list inside the APK, and that list is rebuilt from the open
Radio-Browser directory by `.github/workflows/refresh-stations.yml`:

- monthly, on demand, or whenever `tools/fetch_stations.py` / `tools/curated_stations.json` change;
- every hand-picked stream in `tools/curated_stations.json` is **probed** first, and only the
  ones that really answer with audio (or a valid HLS playlist) are shipped;
- the result is committed to `radio/src/main/assets/stations_seed.json` and packaged into the
  next APK, so a build never ships a station link that was already dead at build time.

## What's new

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
