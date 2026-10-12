# Changelog — maintained Android apps

## Live Guard 2.42/code47 — published 12 October 2026: the owner's nine-item work list

**Published** as stable/latest `v2026.10.12-2`, package `com.universalrp.cleansweep`, unchanged
stable signing key, minSdk 26, target/compileSdk 36. Ramesh Radio 1.8 and PulseEQ 1.2 are
unchanged and travel as their previously verified bytes.

> **No device test covered these bytes.** 2.42 is published without a recorded physical-device
> install, which the owner's 12 October 2026 decision permits because the release **adds no
> dangerous permission** (see the publication-rule change below). CI compiled the app, ran all
> **349** required cases in **both** the debug and release variants with no skips, and passed the
> APK/AAB, package, version, API 26, non-debuggable, stable-certificate and size gates. That
> verifies the build; it is not a device test. Two items are **open device work**, not proven
> fixes: the OPPO A3s charging watts and the per-ROM permission routing —
> [`docs/live-guard-2.42-device-test-plan.md`](docs/live-guard-2.42-device-test-plan.md). The
> published digest is recorded in the tag run's log and in
> [`docs/candidate-build-digests.md`](docs/candidate-build-digests.md). Scope, decisions and the
> two judgement calls: [`docs/live-guard-2.42-scope.md`](docs/live-guard-2.42-scope.md). Full
> notes: [`releases/v2026.10.12-2.md`](releases/v2026.10.12-2.md).

- **Charging watts on phones whose current counter reports nothing** (`data/BatteryValuePolicy.kt`,
  `data/DeviceHealth.kt`): the OPPO A3s regression — `0.00 W` / `-0.00 A` while charging, though
  its own charge history recorded real 4.6–4.8 W sessions. The current was read only from
  `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`, which older/OEM kernels answer with `0` or
  "unsupported" while the broadcast extra `BatteryManager.EXTRA_CURRENT` still carries the real
  charge current; `4.36 V × 0 A` then looked like a measurement. One policy now decides which
  reading may be used **and records its source**: a real non-zero property value, then the
  broadcast extra, then a broadcast zero (a genuine trickle/full reading), otherwise nothing. A
  bare property `0` with no broadcast corroboration is *not* reported as 0 A — an unsupported
  sensor is still never rendered as `0.00 W`. `microAmpsToAmps` remains the single unit decider
  (magnitude is never treated as evidence of a unit) and `ChargingHonestyPolicy` the single watts
  decider, so the card, health screen, pill, history and voice still cannot disagree. When the
  broadcast rescued the reading, the screen says so. A test reproduces the owner's own evidence:
  4.36 V with a zero property and a 1 100 000 µA broadcast yields **4.80 W**.
- **Voice A / Voice B chosen automatically** (`voice/AnswerVoicePolicy.kt`,
  `voice/AnswerVoicePreferences.kt`, `MainViewModel.kt`): the *"Choose and preview two voices…"*
  error is gone — the string was removed from the catalog with its Tamil pair so it cannot
  return. With no saved choice, the first two usable installed voices for the answer's language
  become A and B; with one, both slots use it; with none, nothing is spoken and the voice
  diagnostics report the real stage instead of an error in the chat. Tamil and English stay
  separate, a saved voice the engine dropped is replaced, and an empty inventory is treated as an
  uninitialised engine rather than as evidence against the saved pair. The Voice & daily watch
  card stays and now says A/B are automatic and can be previewed or swapped; a **manual** save
  still needs a completed preview and two distinct voices, and each answer is still read once in
  its assigned voice.
- **Ongoing meter and background monitor default ON and survive an update**
  (`data/MonitorDefaultsPolicy.kt`, `data/SettingsRepo.kt`, `notify/ChargeMonitorService.kt`,
  `notify/BootReceiver.kt`): both are opt-out now, and `MY_PACKAGE_REPLACED` / `BOOT_COMPLETED`
  re-arm them even when the preference was never written — previously the receiver read a
  preference nobody had set and left the meter dark after every update. One policy object owns
  the defaults for the DataStore flow, the plain-prefs mirror and the UI state, which used to
  carry three separate literal `false` values and could disagree. A saved OFF is still honoured
  and never overwritten; Settings now says turning one off is a choice, not a failure, and the
  reviewed-setup entry keeps the OEM/force-stop caveat. The hourly health watch is armed by the
  monitor's own switch too, not only by voice and the daily check.
- **The floating pill deliberately stays opt-in:** Android needs "Display over other apps" before
  anything can be drawn beside the clock and that access is never granted automatically, so
  defaulting it ON would promise a reading the phone has not allowed. Automatic **online** daily
  summaries also stay a separate explicit consent, default OFF — turning on local background
  monitoring is not consent to send report facts to a provider.
- **Security check includes preinstalled system apps by default** (`MainViewModel.kt`,
  `data/MonitorDefaultsPolicy.kt`): the switch labelled "Include preinstalled system apps" in the
  Security screen defaulted OFF; it is ON now, and the security review scope and app-cache scope
  read the same policy object so the three cannot drift. Inclusion is **inspection only** — no
  permission granted, nothing selected or removed automatically — and an unchecked or unknown
  hash still proves nothing; a hit on a read-only preinstalled partition is information, not an
  available action.
- **Permission findings open that app's own permission page** (`data/SecuritySettingsRoutes.kt`):
  **Manage** on the iMobile SMS item used to open MIUI's app-level "Other permissions" hub — a
  different page listing MIUI extras such as autostart and pop-up windows. Two causes fixed: the
  generic all-permissions action was fired first (MIUI claims it and ignores the package), and
  the package travelled only as the `PACKAGE_NAME` extra, never as the intent's `package:` data
  URI. Routes are now ROM-aware and most-specific-first — the ROM's own package-scoped editor on
  a Xiaomi, then the editor for the **exact** permission (`MANAGE_APP_PERMISSION` +
  `EXTRA_PERMISSION`), then the app's whole permission list, then App info as the guaranteed last
  resort — and every candidate carries the package both ways. ColorOS is recognised with its own
  manual path. No OEM component name was invented: a candidate the ROM does not expose fails to
  resolve and the next is tried, ending in the existing honest manual-path text. Routing differs
  per ROM and release, so it still needs checking on both phones.
- **Main screen: one spacing scale, nothing clipped** (`ui/HomeLayoutPolicy.kt`,
  `ui/HomeScreen.kt`): the home `LazyColumn` had **no top padding at all**, so the first card was
  drawn flush against the header and LED strip; the screen also mixed four hand-picked edges
  (14/16/14/16 dp) with card padding at 12/14/16 dp. One 4 dp-based scale now owns every edge,
  gap and card padding — one screen edge, an even card gap raised from 14 to 16 dp so rows no
  longer crowd, a real top inset, a bottom inset, two card-padding sizes only, and
  `onScale`/`scaled`/`isCoherent` so a one-off number is corrected instead of shipped. This is a
  *layout* contract: clipping at the largest font scale, on a small screen or in split screen
  still needs a phone. Typography was left alone rather than "improved" blind.
- **One language source of truth** (`data/LanguageSourcePolicy.kt`, `MainActivity.kt`,
  `ui/AppRoot.kt` and six screens): cards could render the opposite language from the header.
  `tr()` read the process-wide language while several composables read the ViewModel's copy with
  nothing keeping them equal, and on a cold start the process default was English until the
  settings flow emitted. Worse, a card observing no state (the update card) was skipped by Compose
  on a toggle and kept text composed in the old language — which is why *"Verified in-app
  download; Android confirms installation."* stayed English in **both** directions. The work list
  reported that pair as missing from `data/Lang.kt`; it was **not** missing, it has been in the
  catalog since the update card shipped, and a test now asserts it and the four other named cards
  have real Tamil values — a duplicate would have failed the localization gate. There is now one
  authority, resolved from the saved choice **before** the first composition and updated on every
  toggle, and the screen tree is rebuilt on a toggle so every string re-reads it. All display
  sites moved onto the authority; the state copy exists only to trigger the rebuild, and the
  source contract fails the build if a display site reads it again. The owner's phone-age estimate
  stays on the home dashboard, still labelled an estimate.
- **Publication rule: device evidence only for a new dangerous permission**
  (`.github/workflows/build-apk.yml`, `tools/verify_release_permission_delta.py`): owner decision
  that supersedes the every-release install rule. The gate now requires
  `docs/live-guard-<versionName>-device-evidence.md` only when the build **adds** a dangerous
  permission versus the previously published release APK, compared on the built artifacts with
  `aapt2 dump badging` — never on the source manifest, because only the merged manifest shows
  what the phone is asked to grant. Appop/signature permissions are correctly not counted as
  runtime prompts, and the tool **fails closed** when the previous release cannot be read.
  Unchanged and still enforced: when an evidence file exists, publication publishes only the
  tested bytes (a rebuild reproducing the recorded digest, else the recorded run's promoted
  artifact), so a rebuilt digest can never inherit an earlier device test; Play Protect is never
  disabled or bypassed; the package and signing key never change; and notes must never claim a
  device test that did not happen. The policy and its reason are recorded in
  [`AGENTS.md`](AGENTS.md) and [`PUBLISHING.md`](PUBLISHING.md). 2.40 and 2.41 were published
  under the older, stricter rule and their records are untouched.
- **Gates:** 349 required cases / 39 suites each variant (new suites for the battery-current
  source, automatic answer voices, monitor defaults, language authority and home spacing; the
  retired reader's 12 historical cases still run from test sources); **945** EN/TA catalog keys
  with no duplicates or placeholder mismatches (five new strings with Tamil pairs, one retired
  string removed with its pair); the retired notification-reader capability still absent from the
  manifest, production sources, build scripts and compiled DEX; 16 `tools/tests` unit tests
  including 8 for the new permission-delta gate. Static and JVM checks only — not
  Kotlin/Compose rendering, not device delivery, not Play Protect.

## Live Guard 2.41/code46 — verified/published 12 October 2026: honest charging readings, 44 °C heat warning, AI budgets

**Published** as stable/latest `v2026.10.12-1` after the owner installed the exact candidate
APK on the phone with Play Protect **ON**. The published APK is the exact device-tested build:
`CleanSweep-v2.41.apk`, **13,565,004 bytes**, SHA-256
`f728a3435f12952370759feed743431c01179b95e84ba48259bae89a35ba7a03`, built in CI run
**38202739572** (source `e31828f74bbe25f6c8e59c278284c06fe24826f3`). With Play Protect **on**, on
the same Redmi 13 5G as 2.40, Play Protect showed "App scan recommended" → "Scanning…" →
"This app looks safe — You can continue to install it", Android's installer completed and
About shows CleanSweep v2.41, upgrading in place over 2.40 (45). That is one phone, one build —
not Google's approval, not all-device certification and not proof of any cause. Record:
[`docs/live-guard-2.41-device-evidence.md`](docs/live-guard-2.41-device-evidence.md). Full
write-up: [`releases/v2026.10.12-1.md`](releases/v2026.10.12-1.md).

- **Honest watts/amps** (`data/ChargingHonestyPolicy.kt`): charging power and current are
  labelled battery-side, shown only when measured, never substituted for one another and
  never turned into `0.00 W` when a sensor is missing. Implausible kernel values
  (volts outside 2–12, current beyond 20 A, watts beyond 150 W, non-finite spikes) are
  refused. The battery reader now uses the same policy, so the card, the health screen and
  the pill cannot disagree.
- **Screen-on/off charging note**: while charging, the card and the battery screen explain
  that the screen's own draw changes the battery-side figure, and that it is measured at the
  battery — not the charger adapter's output. Plain measurement note, no fault and no claim
  about the charger.
- **AI output-budget + provider model check** (`ai/AiOutputBudgetPolicy.kt`): every
  generation ask is clamped to 32–4 000 output tokens (listing models keeps 0), a request
  whose deadline is nearly spent asks for a *shorter* answer instead of timing out, and a
  model absent from the provider's own `/models` list is rejected with the existing
  translatable message — in the connection test and after "Load models". An unavailable list
  is never evidence against a model.
- **44 °C heat warning** (`data/ThermalWarningPolicy.kt`): one owner-set battery threshold
  (44 °C; the voice path used 45 °C and one plug-in check used 42 °C), the sensor is named
  honestly (battery vs CPU zone), the temperature text is formatted from the real reading or
  omitted, and a repeat is hushed for 3 hours. No cause and no diagnosis are claimed.
- Gates: **36 required suites / 321 prepared declarations** (`tools/verify_cleaner_regressions.py`
  +3 suites, +20 declarations), 941 audited EN/TA catalog keys (931 before), the unchanged
  core-manifest/retired-capability gate, plus a new CI DEX check that `batterySideWatts`,
  `screenNote`, `shouldAnnounce`, `effectiveOutputTokens` and `checkModel` are compiled into
  **both** shipped variants. Publication required the recorded device test above; the
  device-evidence gate published the tested bytes and was not weakened.

## Live Guard 2.40/code45 — verified/published 11 October 2026: notification/message reading removed

**Published** as stable/latest `v2026.10.11-4` after Google Play Protect blocked
installation of the published Live Guard APK ("App blocked to protect your device") and
the owner chose to ship the main app **without notification/message reading**. The
screenshot shows no hash and Google gave no hash-linked verdict, so the blocked artifact
is unconfirmed and the 2.38/2.39 reader is the leading suspect, not a proven cause; its
removal is not a guaranteed clearance. The published APK is the exact device-tested
build: `CleanSweep-v2.40.apk`, **13,558,548 bytes**, SHA-256
`9a802bffc6e4ff17bc572be3c19f612f92a23c133790042f87234da20fe20869`, built in CI run
38149080790 (job 114498516971, source `92ffd31b4cf8a4141769ca5dd1d141354807eefa`).
With Play Protect **on**, on one Redmi 13 5G (Xiaomi HyperOS
3.0.303.0, Android 16, build BP2A.250605.031.A3) Play Protect showed "This app looks
safe - You can continue to install it" and Android's installer completed; About shows
CleanSweep v2.40. The same
phone had blocked the 2.39 APK. This is one phone, one build — not Google's approval, not
all-device certification and not proof of the cause. Record:
`docs/live-guard-2.40-device-evidence.md`.

- Removed completely (not switched off, not hidden): `MessageReaderService`, the
  `BIND_NOTIFICATION_LISTENER_SERVICE` permission, the `NotificationListenerService`
  intent filter, the reader screen/route/links/setup row, `SetupGuides.NOTIFICATIONS`, the
  reader-only ML Kit translate/language-id dependencies, the reader service-label
  resources, and the private-message speech plumbing
  (`Announcer.KEY_MESSAGES`/`Event.MESSAGES`/`speakNotification`/`cancelNotifications`).
  The 2.39 sources are archived byte-for-byte in
  `docs/archived-notification-reader-2.39/` (reference only; never compiled, no hidden
  flavor/module, never distributed as a workaround). Re-adding any notification/message
  access needs a separate owner decision plus policy and device review.
- The retired pure `NotificationReadingPolicy` now lives in the **test** source set so its
  12 historical cases still run. CI rejects the retired class names in **both** debug and
  release APK DEX, and `tools/verify_cleaner_core_manifest.py` (+8 unit tests) checks the
  source manifest, the merged manifest, the production tree, the archive discipline and
  removed dependencies.
- One-time scoped cleanup (`CorePrivacyMigration`, 6 prepared cases) retires only the
  reader's preferences file, the `voice_messages` key, the reader's stale voice diagnostics
  (only when `voice_last_event == "MESSAGES"`) and the obsolete `reader` setup-reminder id.
  A completion marker is written only when every step succeeds, so an interrupted cleanup
  retries on a later start; nothing private is logged. API keys, language, A/B voice
  selections, switches (including saved OFF), data plan, histories, genuine
  permission-review records and other apps' data are untouched.
- Kept: `POST_NOTIFICATIONS` and every ordinary Live Guard alert, general speech, installed
  voice previews, the two selected A/B answer voices, widgets, health/cleanup/security/data
  features, the verified updater with mandatory Android installer confirmation, package,
  stable certificate, API 26 support and all supported ABIs. Both languages now state the
  no-notification-reading position (931 EN/TA keys audited).
- Gates run before publication: **301 cases in each** Cleaner debug/release variant
  (**33 suites**), `:radio:testDebugUnitTest`, all-module APK/AAB and
  package/version/API26/non-debuggable/stable-signature/packaged-feature checks, the
  retired-capability/DEX gate and measured APK size (128MiB cap plus the existing 2.39
  client's 50MiB bound) — all green in run 38149080790. Publication additionally
  required the owner's recorded Play Protect + installer outcome for the exact APK
  SHA-256 in `docs/live-guard-2.40-device-evidence.md`; the workflow refuses a newer
  CleanSweep version without it and publishes the tested bytes (a rebuild with a
  different digest falls back to run 38149080790's `cleansweep-apk` artifact after
  digest verification). Previous release assets and CI records are never overwritten or erased.

## Live Guard2.39/code44 — verified/published11 October2026

Follow-up to the passed/published2.38: its universal APK is82,630,093 bytes,
above the updater's50MiB limit.2.39 uses a128MiB bounded cap, compresses/extracts
native libraries without dropping supported ABIs, and gates publication on the
actual APK fitting the updater. Same package/certificate/API26. No changed file
is uploaded under the existing2.38 version. Unchanged Radio/EQ assets are carried
byte-for-byte from verified2.37; rebuilt changed-hash companions are not reused.
All2.38 functional changes below retained; all295 required cases EACH variant,
all-module/signature and new size gates passed again in CI38143638654/job114482383017
(6m00s), source6790a96…, published13:38:57Z as stable/latestv2026.10.11-3.
Actual universal APK44,610,589 bytes fits new128MiB and old50MiB caps. Device tests
remain pending; release assets/tag/latest/source/digests and copied companion bytes
were independently checked. Older2.37/2.38 assets remain untouched.

## Live Guard2.38/code43 — verified/published; superseded by2.39 size correction

Owner now authorizes the next build and verified publication on11 October2026.
Candidate **2.38/code43**, tag `v2026.10.11-2`; do not publish failed builds or
replace the existing **2.37/code42** assets. CI38142967677 succeeded (5m57s); all295 required cases each variant,
all-module builds and APK/API26/signature gates passed. Published11October2026
13:28:59Z from `f04467580a6e6a2b98984634d6d56cc53c58f5cb`.
Its assets stay immutable; the discovered updater-size issue is fixed in2.39.

- Reviewed feature setup with saved-OFF respect, explicit access/voice/background
  choices and optional quiet aggregate reminders. No blanket permission grant.
- Cleaner-only Home Update control: eligible official-release pulse, bounded
  browser-free private APK download, hash/package/version/signing checks, explained
  unknown-apps access and mandatory Android installer confirmation. No forced or
  bypassed Play Protect claim. Radio/PulseEQ shared updater remains unchanged.
- Opt-in multi-app/multi-sender notification reading: sender-only default,
  per-app full-message option, consented local EN/TA language models and non-network
  installed TTS. No notification-to-AI/upload/history path; unknown translation
  means sender only. Android/OEM/privacy gates still apply.
- Nearby official provider/VirusTotal key links and bilingual text/voice guides.
  Exact LM Arena source/category/date/no-match card; the verified public feed is
  December2025, so it is dated, **not a current October2026 score or IQ**.
- Three compact launcher widget profiles replace the large `% + W` default:
  current battery, genuine observed unplug time/level, metered usage or useful
  Wi-Fi/user-set unlimited5G phone details. Floating pill unchanged. System-app
  review/cache inclusion defaults ON when unset; cache opens fresh without new
  automatic selections/deletion.
- Previous 45-second cancellable chat/exact-product/TODAY news, localized stable
  errors, mode-aware welcomes, genuine saved-review/12-hour times and actual A/B
  voice-preview/alternate-between-answers fixes retained. Heavy report/device-AI
  whole operations now have owned 90-second progress/cancel/stale-result gates;
  network scan+AI uses one batch and never saves guesses as verified user names.

**Actual2.38 validation:** CI38142967677 succeeded, all295 required cases EACH
variant and all-module/API26/non-debuggable/signature/packaging gates passed; no
phone/live-provider tests. All22 shipped tests unchanged in source.2.39 repeats
that gate with size/companion immutability checks, recorded above. Dynamic legacy
translation meaning/layout and device behavior remain uncertified. See
[next-update review](docs/live-guard-next-update-review.md). Previous assets unchanged.

Detailed historical notes remain in [`releases/`](releases/). This summary does not
claim app-store publication or physical-device certification.

## CleanSweep / Live Guard 2.37 (code 42) — 11 October 2026

**Verified/published** as `v2026.10.11-1` at2026-10-11T09:08:41Z, immutable source
`325576df99dc3d56ad635e5a2d229a0e468dd7da`. [CI 38127107781](https://github.com/universalrp2003/InternetRadio/actions/runs/38127107781) passed 177 required cases
in each Cleaner variant plus build/feature/version/API26/signing gates; six assets checked.
See [release notes](releases/v2026.10.11-1.md) for details and physical-device limits.

- Automatic TTS main-thread initialization, bounded per-language queue, callbacks,
  checked return values, awaited background events, one same-language offline retry,
  start-based cooldowns and connection/job deduplication. Runtime power/battery/network
  receivers are owner-scoped; registration evidence is shown, and manifest-only
  cold-background power delivery is not guaranteed. Voice diagnostics distinguish
  queued/blocked/failed/engine outcomes; real-gated automatic test and guarded TTS settings.
- Real publication-today Tamil RSS/Atom news in phone-local timezone, canonical title/URL
  deduplication, day-scoped unseen rotation ending when caught up, no publisher ribbon,
  old/static/synthetic fallback or refresh reset of seen titles.
- Local configured API/model identity answers without web/API calls; relevant unique
  collapsed evidence for other questions, not an unrelated first-hit Wikipedia fallback.
- Larger resizable launcher widget: battery% + battery-side W, per-widget views,
  available measurements and snapshot time, unique actions and coalesced off-main queries.
  Only genuinely reported fine percent resolution; two W decimals do not certify accuracy.
- Correct microamp API conversion without a magnitude multiplier; fractional SOC does
  not falsely certify exact20→80 charging-history boundaries.
- Retain active Home phone-age estimate, all six 2.35 tools/privacy controls, targeted
  settings and floating pill behaviour. Add52 required cases, retaining all 125 prior.
- Ramesh Radio1.8/PulseEQ1.2 and the two legacy modules unchanged in source.

## CleanSweep / Live Guard 2.36 (code41)

[Verified release](releases/v2026.10.11.md): restored wrapping/tappable estimated-age
badge to active Home with truthful unsupported evidence and off-main daily cache.
125 required Cleaner cases each variant. All six2.35 improvements retained.

## CleanSweep / Live Guard 2.35 (code40)

[Verified release](releases/v2026.10.10-9.md): truthful dated dashboard, allowlisted
compatibility diagnostics, opt-in security history, observed charging/charts,
conditional configurable data budgeting/history, and grounded manually reviewed
checklists/fresh rechecks.113 required Cleaner cases per variant.
