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

## ⚠️ Installation-block advisory — read before installing Live Guard

Google Play Protect blocked installation of the published Live Guard **2.39** APK on the
owner's phone with **"App blocked to protect your device — App: Live Guard"**. The
screenshot does not show which APK hash was blocked, and Google has not given a
hash-linked verdict, so the exact blocked artifact is **unconfirmed**. The leading
suspect is the opt-in notification-reader capability shipped in 2.38/2.39:
`BIND_NOTIFICATION_LISTENER_SERVICE` and the `NotificationListenerService` intent filter
are declared inside the package, so runtime OFF does not remove them.

- **Live Guard 2.40 with that capability completely removed is now published** (tag
  `v2026.10.11-4`). With Play Protect **on**, the exact recorded APK digest installed and
  opened on one Redmi 13 5G: Play Protect showed "This app looks safe - You can continue
  to install it" and the installer completed ([device evidence](docs/live-guard-2.40-device-evidence.md)).
  That is one phone, one build — **not** Google's approval, **not** all-device
  certification and **not** proof that the removed reader caused the 2.39 block. Removing
  one capability is still not a guaranteed Play Protect clearance.
- **Do not** disable or bypass Play Protect, hide the download source, rename the package
  or change the signing identity. Those are concealment tactics, not fixes.
- Ramesh Radio and PulseEQ are unaffected and keep their previously verified assets.
- The published 2.39 APK stays online unchanged as history; installing it on the affected
  phone can still be blocked — see the advisory.

Full advisory, official Google references and the next steps:
[`docs/live-guard-play-protect-install-block.md`](docs/live-guard-play-protect-install-block.md).

## Live Guard 2.43 — published (12 October 2026)

One focused fix from the owner's own device test of 2.42: on the OPPO A3s the kernel answers
**zero through both** battery-current paths, which is indistinguishable from an unsupported
sensor, so 2.42's "broadcast zero is a trickle" branch reprinted the fake `0.00 W` the owner
reported. From 2.43 **a zero is never a measurement**: a non-zero property value wins, then a
non-zero broadcast value, otherwise the current reads *not reported* and no watt figure is shown.
Phones with a working counter are unchanged — a real trickle is a small non-zero value. This release
also records the owner's 2.42 install evidence: both phones, Play Protect ON, "App scan
recommended" → "This app looks safe — You can continue to install it" → installed in place over
2.41, digest matching the published asset
([evidence](docs/live-guard-2.42-device-evidence.md)). **No device test covered the 2.43 bytes
before publication** (no dangerous permission was added); published as `CleanSweep-v2.43.apk`,
13,574,748 bytes, SHA-256 `6f922f078e3561890aae13a38e4bc6387e3bed3a26da16d5a22591e8596657d7`. One A3s screenshot
while charging will confirm the new wording — see
[`docs/live-guard-2.42-device-test-plan.md`](docs/live-guard-2.42-device-test-plan.md).

## Live Guard 2.42 — published (12 October 2026)

The owner's nine-item work list is **published as 2.42 / code47**: charging watts come back on
phones whose battery current counter reports nothing (the OPPO A3s `0.00 W` regression — the
battery broadcast extra is now the fallback, and an unsupported sensor is still never shown as
`0.00 W`), Voice A and Voice B are chosen automatically from the installed voices instead of
refusing with an error, the ongoing status-bar meter and the background battery-health monitor
default ON and are re-armed by an update or reboot, the Security screen's "Include preinstalled
system apps" defaults ON, **Manage** on a permission finding opens that app's own permission page
instead of MIUI's "Other permissions" hub, the main screen uses one spacing scale with a real gap
under the header, and the whole UI reads one language source of truth so no card renders the
opposite language from the header.

> **No device test covered these bytes.** 2.42 was published without a recorded physical-device
> install, which the owner's 12 October 2026 decision permits because the release **adds no
> dangerous permission** — the publication gate compared this build's merged-manifest permissions
> with the published 2.41 APK using `aapt2`. CI compiled the app and ran all 349 required unit
> tests in both variants; that verifies the build, not a phone. The OPPO A3s charging reading and
> the per-ROM permission routing are **open device work**:
> [`docs/live-guard-2.42-device-test-plan.md`](docs/live-guard-2.42-device-test-plan.md). Scope and
> decisions: [`docs/live-guard-2.42-scope.md`](docs/live-guard-2.42-scope.md) · notes:
> [`releases/v2026.10.12-2.md`](releases/v2026.10.12-2.md).

## Live Guard 2.41 — published (12 October 2026)

Owner-requested follow-up work on the 2.40 core is now **published as 2.41 / code46**: charging
watts and amps are labelled and shown only as measured battery-side values (never substituted
for each other, never guessed), a screen-on/off note explains why the same cable can read
differently, the AI asks carry a bounded output budget with a provider `/models` check that
fails fast instead of producing a doomed request, and the battery heat warning is one
owner-set **44 °C** threshold with a 3-hour hush. With Play Protect **on**, the exact recorded
APK digest installed in place over 2.40 on the owner's Redmi 13 5G; About shows CleanSweep
v2.41 ([device evidence](docs/live-guard-2.41-device-evidence.md)) — one phone, one build, not
Google's approval or all-device certification. Detailed notes:
[`releases/v2026.10.12-1.md`](releases/v2026.10.12-1.md) · device test plan:
[`docs/live-guard-2.41-device-test-plan.md`](docs/live-guard-2.41-device-test-plan.md).

## Download — release APKs

**2.43 is the current published Live Guard release** (12 October 2026, unchanged
package/signing key, minimumAPI26) — install it over your existing app rather than
uninstalling, and see the advisory above. **No device test covered the 2.43 bytes before
publication** (no dangerous permission added); 2.42 *was* installed and observed afterwards on
both of the owner's phones with Play Protect ON
([evidence](docs/live-guard-2.42-device-evidence.md)). If you want a build whose bytes were
verified on a phone **before** it was published, use
[2.41](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.12-1) — the exact
device-tested build (13,565,004 bytes, SHA-256
`f728a3435f12952370759feed743431c01179b95e84ba48259bae89a35ba7a03`): on one Redmi 13 5G with
Play Protect **on**, Play Protect showed "App scan recommended", scanned the app, showed "This
app looks safe — You can continue to install it" and the installer completed, upgrading in place
over 2.40 ([device evidence](docs/live-guard-2.41-device-evidence.md)) — one phone, one build, not
Google's approval or all-device certification. Install over your existing app — do not uninstall
and lose settings. [Previous 2.41](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.12-1),
[2.40](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.11-4),
[2.39](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.11-3)
(installation may be blocked — see advisory), [2.38](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.11-2)
and [2.37](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.11-1)
assets remain immutable.

| App | Version | Installable APK |
|---|---|---|
| Live Guard (CleanSweep) | 2.43 | [CleanSweep-v2.43.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.12-3/CleanSweep-v2.43.apk) |
| Ramesh Radio | 1.8 | [Ramesh-Radio-v1.8.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.12-3/Ramesh-Radio-v1.8.apk) |
| PulseEQ | 1.2 | [PulseEQ-v1.2.apk](https://github.com/universalrp2003/InternetRadio/releases/download/v2026.10.12-3/PulseEQ-v1.2.apk) |

[Release notes and all assets](https://github.com/universalrp2003/InternetRadio/releases/tag/v2026.10.12-3)

Ramesh Radio and PulseEQ are unchanged; their assets are the previously verified bytes reused
by the publication step, not rebuilt copies.
· [All releases](https://github.com/universalrp2003/InternetRadio/releases)

Install **one release APK per app**. Debug APKs are development builds; you do not
need both. AABs are for store submissions and cannot be installed directly.
GitHub publication does not mean the apps have been published to Google Play or F-Droid.
CI artifacts are temporary build downloads, not the public release channel.

## What's new

- **Live Guard 2.40 / code45 — verified/published** (tag `v2026.10.11-4`). Core Live Guard
  removes notification/message reading completely: the reader service, its
  `BIND_NOTIFICATION_LISTENER_SERVICE` permission, its `NotificationListenerService`
  intent filter, its screen/route/links, its ML Kit translation dependencies and its
  service-label resources are gone; the 2.39 sources are archived
  ([reference only](docs/archived-notification-reader-2.39/README.md), never compiled).
  A one-time cleanup retires the removed reader's private leftovers only. Ordinary Live
  Guard alerts, general speech, voice previews, A/B answer voices, widgets, health/
  cleanup/security/data features and the verified updater are kept. CI rejects the
  retired classes in **both** debug and release APK DEX and validates the source **and**
  merged manifests. The published APK is the exact device-tested bytes — **13,558,548
  bytes**, SHA-256 `9a802bffc6e4ff17bc572be3c19f612f92a23c133790042f87234da20fe20869`
  (run 38149080790, source `92ffd31b…`) — and with Play Protect **on** on one Redmi 13 5G
  Play Protect showed "This app looks safe - You can continue to install it" and the
  installer completed ([device evidence](docs/live-guard-2.40-device-evidence.md)); that one
  result is not Google's approval, not all-device certification and not proof of the
  2.39 block's cause. [Release notes](releases/v2026.10.11-4.md) ·
  [Preparation notes](docs/live-guard-2.40-core-preparation.md)
  · [Permission audit](docs/live-guard-2.40-permission-audit.md)
  · [Install-block advisory](docs/live-guard-play-protect-install-block.md).

- **Live Guard2.39/code44**: reviewed feature setup with saved-OFF respect;
  Home verified in-app APK download/Android confirmation; opt-in selected app/
  sender reading, sender-only default, consented local EN/TA translation and
  non-network speech (**that reader is removed in the 2.40 release above**);
  nearby official key/voice guides; exact/datable LM Arena
  source card (the December2025 feed is **not current or IQ**). Three compact
  Battery/Data/Phone widgets with genuine observed-unplug details supersede the
  large launcher widget; floating pill unchanged. System/cache scope defaults
  ON when unset, refresh without automatic selection/deletion. Shared chat45/
  heavy90 budgets, progress/cancel, exact-product/TODAY news, genuine last-review
  date, installed A/B answer previews and all six shipped improvements retained.
  Actual44.6MB delivery and both update-client caps checked, allABIs preserved.
  No actual-device/audibility/privacy/native-runtime guarantee.
  [Release notes](releases/v2026.10.11-3.md) ·
  [2.39 device plan](docs/live-guard-2.39-device-test-plan.md).

- **Live Guard / CleanSweep 2.37:** automatic voice delivery now checks
  initialization, actual callbacks and failures, with awaited power jobs and a real-gated
  automatic test/diagnostics and owner-scoped runtime power/network/battery listeners.
  Manifest-only cold-background power delivery is not guaranteed. Today's dated Tamil headlines only, finite unseen rotation,
  no publisher ribbon or old/safety-tip fallback. API/model identity is answered locally
  without irrelevant duplicated web evidence. Larger resizable launcher widget defaults
  to **battery% + battery-side W**, with per-widget view cycling and truthful precision;
  current-unit guessing is removed. The Home phone-age badge, all six tools and separate
  floating pill are retained. **177 required cases per variant passed** the cloud
  build/feature/version/API26/signing gates; this is not a physical-device/audibility/
  feed-availability pass.
  [Release notes](releases/v2026.10.11-1.md) ·
  [2.37 device plan](docs/live-guard-2.37-device-test-plan.md) · [Changelog](CHANGELOG.md).
- **Live Guard / CleanSweep 2.36:** the **Estimated phone age**
  badge is back on the active Home dashboard, with wrapping English/Tamil text and
  a tap-to-explain basis. It is a coarse model/initial-Android-generation estimate,
  not this handset's manufacture, purchase or first-use date. Unsupported evidence
  stays unavailable; current Android updates, security patches and a guessed 2022
  date no longer masquerade as age. Calculation is off the UI thread and cached daily.
  All six 2.35 improvements and earlier charging/settings/AI behaviour are retained.
  The published build passed **125 required regression cases per variant**
  (113 retained + 12 age) plus compilation/feature/version/API26/signing gates.
  The owner's request to retain this badge is recorded with regression guards. See the
  [2.36 device test plan](docs/live-guard-2.36-device-test-plan.md).
- **Shipped in 2.35 and retained in 2.36:** all six improvements: honest dated dashboard,
  phone compatibility self-test with previewable safe diagnostics, opt-in local
  security changes and charging history/charts, expiry-aware data budgeting/history,
  and grounded AI-linked Do first / Optional / Leave alone review steps.
  No made-up readings, automatic security actions or silent background-monitor enablement.
  Histories default off and have bounded private retention/clear controls.
  Watts-only charging, normal unplug behaviour, relevant settings routes and fresh
  privacy-controlled AI reports are retained. CI enforces **113 required regression
  cases per CleanSweep variant** plus existing tests/build/signing gates. See the
  [2.35 device test plan](docs/live-guard-2.35-device-test-plan.md).
  The owner's earlier **Redmi 13 5G** confirmation is limited to **2.34 charging/display**;
  no new 2.35 feature or all-manufacturer physical test pass is claimed.
- **Ramesh Radio 1.8** and **PulseEQ 1.2** are unchanged maintained companions;
  all maintained/legacy modules remain in the build.

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

**Verified/published2.40/code45:** the exact device-tested `CleanSweep-v2.40.apk`
(**13,558,548 bytes**, SHA-256
`9a802bffc6e4ff17bc572be3c19f612f92a23c133790042f87234da20fe20869`) was built in
[CI 38149080790](https://github.com/universalrp2003/InternetRadio/actions/runs/38149080790)
/job114498516971 from source `92ffd31b4cf8a4141769ca5dd1d141354807eefa` with all
required gates green (301 required cases EACH Cleaner variant, 33 suites, Radio tests,
all-module APK/AAB, package/version/API26/non-debuggable/stable-certificate/DEX/manifest
and both updater-size gates). Those bytes were installed on the owner's Redmi 13 5G
(Xiaomi HyperOS 3.0.303.0, Android 16, build BP2A.250605.031.A3) with Play Protect
**on** — Play Protect said "This app looks safe - You can continue to install it", the
installer completed and About shows v2.40 — and they are what the device-evidence
publication gate publishes (a rebuild with a different digest is never substituted; the
gate falls back to run 38149080790's `cleansweep-apk` artifact bytes). Stable/latest
**v2026.10.11-4**. That install result is one phone, one build: not Google's approval,
not all-device certification and not proof of the 2.39 block's cause. Full record:
[device evidence](docs/live-guard-2.40-device-evidence.md).

**Verified/published2.39/code44:** [CI 38143638654](https://github.com/universalrp2003/InternetRadio/actions/runs/38143638654)
/job114482383017 succeeded in **6m00s**, immutable compiled source/tag `6790a96b591b5a3215f5f28193726f0cd607c04f`.
Both Cleaner variants executed all **295 required cases EACH** (177 retained+118 new,
32 suites, none skipped), plus Radio regressions and all-module debug/release APKs
and AABs. APK version/package/API26/non-debuggable/stable-certificate/packaged-feature
and actual updater-size gates passed. Published **2026-10-11T13:38:57Z**, stable/latest
**v2026.10.11-3**, with all six assets checked independently through GitHub API.

This 2.39 record is kept as published history: its CI results are not erased, and its
asset bytes are never replaced. Its notification reader is not part of 2.40,
and installation of 2.39 can currently be blocked by Play Protect — see the advisory above.

Universal APK: **44,610,589 bytes (44.6MB /42.5MiB)**, GitHub SHA256
`f29daac53da8e45cc8f278a93f7ffe9c78d36136f13497d16d93fb6f6fa89b98`. It fits both current128MiB and the
previous2.38 client's50MiB cap. All supported native ABIs are retained; compression/
extraction reduces download bytes, not an all-device native-runtime certification.
Unchanged Radio1.8/EQ1.2 assets are byte-for-byte/digest-identical to verified2.37,
not regenerated changed-hash copies. Previous2.37 and2.38 assets remain untouched.
Stable certificate remains
`9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55`.

No physical-phone/emulator, newly heard automatic speech, OEM background, message/
translation/redaction, live-provider/rating/feed or Android installation test is
claimed. Sandbox CDN restrictions prevent independent local binary download;
package/signature evidence is exact-source CI and public asset digests are GitHub
metadata. See the [device plan](docs/live-guard-2.39-device-test-plan.md).

**2.37 publication verified:** [GitHub Actions passed](https://github.com/universalrp2003/InternetRadio/actions/runs/38127107781) in **3m46s** for
immutable source `325576df99dc3d56ad635e5a2d229a0e468dd7da`. Both Cleaner variants ran all 177 required cases
(125 retained + 52 new), with existing Radio tests, all-module builds and packaged-feature/
version/API26/non-debuggable/signing gates. All six maintained-app APK/AAB assets and
GitHub digests were verified against the stable tag/latest/source. Previous 2.36 and 2.35
notes, sources and all six asset IDs/sizes/timestamps/digests are unchanged. Certificate:
`9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55`.
No new Redmi audible/background/launcher/feed test pass is claimed; an enabled/queued or
engine-finished voice status cannot prove sound was heard. See the device plan above.

**2.36 publication verified:** [GitHub Actions passed](https://github.com/universalrp2003/InternetRadio/actions/runs/38097130627) for source `041f2ba`: both CleanSweep unit-test variants, all 125 required cases per variant, existing Radio tests, all-module builds and release APK/feature/version/API-26/signing gates. All six maintained-app APK/AAB assets are published; existing 2.35/2.34 assets are unchanged. This is not a physical-device test pass.

See [full release notes](releases/v2026.10.11-3.md), [changelog](CHANGELOG.md), [privacy](PRIVACY.md),
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
