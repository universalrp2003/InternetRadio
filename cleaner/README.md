# Live Guard (CleanSweep) — cleaner, phone health, security & network

A privacy-friendly app targeting **Android 8.0/API 26+**, with guarded settings
routes and manufacturer-aware fallbacks for Xiaomi/Redmi/Poco (HyperOS / MIUI)
and other Android phones. This is a compatibility target, not proof that every
model or ROM has been tested. Manufacturer Settings links, overlays and background
restrictions still need real-device validation.

## v2.35 — current release

[Install CleanSweep-v2.35.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.10-9/CleanSweep-v2.35.apk)
(the launcher is **Live Guard**; package/signing key unchanged).

All six approved improvements are reachable from **Home → History & tools**:

1. **Honest dashboard**: Not checked/measured/reported instead of fabricated scores,
   ping, RAM or storage. Values carry timestamps; a permission score is not malware safety.
   UI reads/probes are off the main thread; a failed/missing probe gives unknown quality.
2. **Phone self-test**: actual model/Android/API/app version, relevant permission,
   notification/channel/configuration and sensor availability checks. Preview before
   copy/share of an allowlisted report; no raw state, keys, app identities, SMS/files,
   prompts/logs, network identifiers or serials. OEM rendering/background/settings
   behaviour remains a physical-device test, not a flag-based certification.
3. **Security timeline**: stable package+service identity; first available baselines,
   newly observed/changed access, manual expected reviews and no-longer-observed findings.
   Failed/partial or reduced-scope reads do not imply resolution. First seen is not
   exact install/grant time; no longer listed is not a proven uninstall or safety verdict.
4. **Observed charging sessions/charts**: percentage, elapsed duration, sampled mean/
   peak battery-side watts, temperature, coverage, observed 20→80 time and comparisons.
   Missed starts/ends, reboot/gaps and unavailable sensors are explicit. Charts show
   nearby samples, not invented continuous sensor data or adapter output.
5. **Data budgeting**: user recharge/period start and expiry/next-reset date, explicit inclusive
   expiry or exclusive next-reset days,
   estimated daily allowance and forecast with at least three usable complete past
   days. Device-wide cellular history excludes Wi-Fi; incomplete/reboot-sensitive
   counters withhold unsupported forecasts. Carrier billing/per-SIM/5G eligibility
   is not inferable. Quota reset/recharge and expiry are separate manual actions.
6. **AI-linked review checklist**: local Do first / Optional / Leave alone–expected
   groups and guarded relevant routes. Optional AI explanations are bound to current
   evidence tokens; arbitrary model intents/URLs/packages/group/completion fields
   cannot become actions. Local fallback works offline. Reviewed does not revoke access
   or resolve a finding; recheck fresh facts. Heat/storage/data/network observations
   open relevant existing tools. No automatic uninstall, permission or file changes.

### Local control and limits

- All three history switches default **OFF**. Enabling history does not enable extra
  persistent monitoring. Charging uses already-running monitoring and visible-app
  readings plus connection events; missed observations remain partial.
- Private no-backup storage only: security **200 events**, charging **30 sessions /
  30 days**, **240 chart points** per session plus latest sample, mobile **90 daily records**.
  Charging expiration is pruned on the next local read; no background cleanup job
  is enabled. Clear controls are on each tab. Off pauses collection but does not erase old history.
  Explicit reviewed choices are saved when selected, even if passive history is off;
  clear security history removes those acknowledgements too.
- The budget screen can query seven complete prior Android-reported days without
  saving them. Today remains partial/in progress. Unknown is different from measured zero.
- No history is silently uploaded. The diagnostic share preview contains only safe
  public device facts and known status IDs. AI sharing remains separately user-controlled;
  historical local timelines/sessions/day records are not added to the AI request.
- Home/live quality uses a small TCP probe to Cloudflare, not a large speed test or
  zero-data monitor. Blocked probes/initial missing jitter stay unknown; one endpoint
  is not a guarantee about every website.

CI enforces **113 required cases for each CleanSweep debug/release unit-test variant**
(27 retained + 86 new), existing Radio tests, all-module compilation and release
APK/version/minSDK/signing/feature checks before publication. [The final publication
run passed](https://github.com/universalrp2003/InternetRadio/actions/runs/38069097825) on source `51d6d7f`; the immutable release uses that source,
not later documentation commits. No phone/emulator is
available in the coding environment. **2.35 still needs real-device/OEM checks** in
[the new test plan](../docs/live-guard-2.35-device-test-plan.md).

## v2.34 fixes retained in 2.35

[Previous CleanSweep-v2.34.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.10-8/CleanSweep-v2.34.apk)
(the launcher is **Live Guard**; package/signing key unchanged).

- While any charger is connected the status pill displays **watts only**. Data/5G
  labels, D/U LEDs and quota alert styling are hidden, not overlapped. Missing
  power is `— W`. Watts are battery-side, not the adapter's advertised output.
- Unplugging restores normal data/LED display with the existing background-monitor
  option enabled. Your preferences, accounting, position and scale are unchanged.
  Charging-only mode still stops on unplug rather than silently enabling persistence.
- Security **Manage** retains the selected app's package/service ID. **Fix/review**
  opens the right category or asks which app. Unsupported OEM shortcuts show a
  named manual path; they never fall through to the Settings homepage.
- **Ask AI about all current issues** takes a fresh app/security/battery/storage
  report. All findings and affected apps are included (when names are shared), plus
  available dated scan results and optional network/data context. Both AI sharing
  switches are honoured. No files, SMS, contacts, file paths or keys enter the report.
- A 100/100 heuristic score does not prove safety. Necessary banking/accessibility
  permissions should not be removed just for a score. Advice is manual and may be wrong.

The previous release's 27 regression tests cover display policy, targeted routes, complete prompts and
privacy. GitHub publication is gated on compilation/tests/APK/signature checks.
After **2.34** publication, the owner confirmed the charging/normal-display transition on
**Redmi 13 5G**, with screenshots. Its Android/ROM version was not supplied; other
models and the full feature matrix remain unverified. See the
[device evidence and test plan](../docs/live-guard-2.34-device-test-plan.md) and
[release notes](../releases/v2026.10.10-8.md).

## Historical highlights

**v2.6 highlights**

- **The scan is fixed** — and honestly: the crash could not be reproduced from here, so three
  real causes were removed instead of guessed at. The home-screen widget is no longer
  refreshed from inside the scan loop (that was a binder call and two file reads on the
  thread walking the disk), the scan engine now keeps two counters per folder instead of the
  name of every file on the phone (the old maps are what an out-of-memory crash on a full
  phone would come from), and everything in the scan path — including `OutOfMemoryError`,
  which is an `Error` and so escaped the old handler — is caught and explained on screen
  instead of letting MIUI say "CleanSweep keeps stopping". The scanner screen also offers a
  **Start the scan** button if it is opened with nothing running (the widget's Clean button
  used to do exactly that), and **About → Last crash** shows the last exception with the
  phone and app version, kept on the phone only.
- **"Run the daily check now" does something now** — it was returning early whenever the
  clock was before the configured daily hour (20:00 by default) and whenever a brief had
  already run that day. A run you ask for is now a real run, at any hour, and it does not
  consume the day, so the evening brief still arrives.
- **The voice screen is readable** — `PanelCard` is a plain surface, not a column, so the six
  new cards' rows were painting on top of each other. Every card now stacks its own content.
- **The charger works in the background** — Android 12+ refuses a foreground-service start
  from a broadcast receiver, which is why plugging in was silent. The plug-in broadcast now
  schedules an expedited job; the job starts the charging service when the system allows it
  and, when it does not, shows the same card (watts, current, voltage, °C, time to full) and
  speaks the same line itself while the cable is in.
- **The AI answers live questions** — with a Google key the assistant asks Google's own
  endpoint with **Search** switched on, so "today's latest news" is answered from the web (up
  to four sources are shown under the answer), and the prompt now says plainly: never refuse
  a question, and if you cannot look something up, say so and answer anyway. The plain
  OpenAI-style request remains the fallback.
- **A more natural voice** — the phone's own cloud voice is preferred over the robotic
  offline one, with the offline voice as the automatic fallback if the network is out. No API
  key, no account, and only the sentence being spoken leaves the phone.
- **Smaller fixes the phone asked for** — **jitter is now labelled next to ping**, the
  **Wi-Fi network name is never shown and never sent** to the AI, and the built APK is named
  **`CleanSweep-v2.6.apk`** instead of `cleaner-debug.apk`.

**v2.5 highlights**

- **A home-screen widget** — battery % and charging watts, free storage, the last daily brief
  in one line, and two buttons: **Clean** (opens the scanner) and **Read brief** (speaks it).
- **More warnings, each with its own switch** — storage below 1 GB, charger removed before 90%
  (only after the hour you set, 06:00 by default), **charger connected but not charging**
  (the worn-cable case, checked 25 s after plugging in), battery health dropping below 85% of
  the original capacity (at most once a week, and only when the kernel exposes the real
  design capacity), **Wi-Fi ⇄ mobile data changes** (off by default — a phone that hops
  networks would otherwise talk all day) and **a new device joining your Wi-Fi** (spoken after
  a network scan, compared against the devices seen last time).
- **The AI has its own language setting** — "Same as I type", always English, or always Tamil,
  independent of the menu language. Answers are read aloud in the language they are written in.

**v2.4 highlights**

- **CleanSweep talks** — battery low, battery full, "running hot", "charging started" and a
  **daily brief** can be spoken out loud. Every one of them has its own switch, and a **quiet
  hours** window (default 22:00–7:00) silences the voice at night — the app cannot talk over
  your sleep even if a warning fires. Anything you ask for yourself is still spoken when you
  ask. If the phone has a Tamil text-to-speech voice, Tamil text is spoken in Tamil.
- **The assistant takes dictation** — a mic button in the chat uses Android's own speech
  recogniser (no RECORD_AUDIO permission, no audio stored), and any answer can be read aloud
  with the "Read aloud" button. Answers are also saved per provider.
- **The AI answers anything now** — the assistant is no longer limited to cleaning topics. The
  on-device facts about your phone are handed to the model as context; when the on-device
  engine cannot answer, the bubble offers **Ask the AI** instead of a dead end.
- **The daily full check** — once a day at the hour you choose, CleanSweep reads the battery,
  temperature, storage, junk and security findings, asks your AI for a two-sentence summary
  when a key is saved, says it out loud and posts the full report as a notification. There is
  a "run the daily check now" button on Home and in Settings → Voice.
- **Charging, on its own** — plugging in starts the charging card *and* the spoken line
  ("Charging started at 42 percent, 12.4 watts now"), even when the app is closed.
- **Fixes** — the ping/jitter test said "no answer, 100% loss" on every target because socket
  calls ran on the UI thread; it now runs on the IO dispatcher and answers properly. The public
  IP check no longer gives up with **HTTP 403** when Cloudflare blocks your VPN — it tries four
  providers (Cloudflare, ipinfo, ipapi, ipify) and names the one that answered. "Mobile data
  looks OFF" no longer appears when data is on and Wi-Fi happens to be carrying the traffic.
- **One-tap language switch** — the Home top bar has a **த/EN** button, next to a speaker
  button that jumps straight to the voice settings.

**v2.3 highlights**

- **Tamil menu** — Settings → **Language** switches between English and **தமிழ்**. The whole
  menu (titles, buttons, cards, settings, dialogs) is translated, and in Tamil the app calls
  itself **சுத்தம் செய்பவர்** (literally "the cleaner" — the Tamil name of CleanSweep). The launcher label is Tamil too on a Tamil
  phone, and Android 13+ shows CleanSweep in *Settings → Apps → Language* because the app
  declares `locales_config`. Anything not yet translated stays in English on purpose rather
  than showing machine-garbled Tamil.
- **Author credit** — About now names **ரமேஷ் பிரதாப்** / Ramesh prathap .R with tappable
  **universalrp2003@gmail.com** and **github.com/universalrp2003**, and shows the app's name
  in both languages.
- **The watt reading moves — two ways** — an arrow pad (Phone health, and Settings → watt
  reading) nudges it 24 px at a time, and **Drag** hands the reading to your finger so you can
  slide it anywhere on the screen and tap **Done**; the spot is remembered, and **Auto**
  returns it beside the camera. Every phone writes different things up there — VoLTE, VPN, the
  carrier name, the battery percentage — so the free space is never in the same place twice.
  Outside drag mode the reading is touch-through and can never block a tap.
- **AI keys are saved per provider** — switching from Gemini to Groq and back restores the key
  *and* the model, so the same key is never typed twice. The AI settings screen shows which
  providers already have a saved key.
- **Busy AI providers are handled properly** — HTTP 502/503/504, rate limits and slow answers
  are retried automatically (up to three attempts with growing pauses), the read timeout is
  180 s, and the message now says whose fault it is and what to do. The screen shows which
  attempt is running instead of looking frozen.
- **New: Mobile & data screen** — operator and SIM names, 5G/4G/3G/2G, the true signal in dBm
  with four bars and a plain-language quality label, cell towers with cell id / PCI / TAC and
  which one is in use, public IP + ISP + city (one small request on demand), **ping and
  jitter** to three public endpoints, a **real speed test** in 1/5/10/25/50/100 MB sizes that
  **asks for the size and never guesses it** — the button is disabled until you choose, your
  last choice is pre-selected next time, and a mobile-data warning names how much the test
  will cost (only the big sizes show true 5G speed), and
  **today's data usage** split into mobile and Wi-Fi with the top data-hungry apps — accurate
  when Usage access is granted, and labelled honestly when it is not.

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
- No ads, no analytics or developer-run accounts; Internet is used for optional AI, news, network tools, hash checks and release checks.

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
- The security score is a **permissions and settings review**, not an antivirus. The
  separate, user-started malware hash card checks known hashes against online databases,
  not app files. Unknown/skipped hashes do not prove safety. The AI does not start that
  scan; it may discuss a previously completed result when you ask for analysis.
- The Wi-Fi device list can only show devices that answer. Sleeping phones, devices that
  block probes, and routers with "AP isolation" will not appear — the screen says so.
- A few phone makers hide the battery **current** counter from apps, and some kernels do
  not expose a CPU temperature sensor. CleanSweep shows "not reported" rather than a
  made-up number.
- AI reports go to your chosen provider (or a disclosed keyless fallback, shared and
  rate-limited) on an explicit analysis/ask tap, or for a configured daily brief.
  News, updates, live quality probes and network tools also use the internet when
  enabled. See [privacy](../PRIVACY.md) for the details.
