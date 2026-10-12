# Live Guard 2.42 (code 47) — scope, decisions and what still needs a phone

Owner work list typed on a phone on **12 October 2026** and pasted into an agent session with
the instruction *"implement all 9 items and publish 2.42 (code 47)"*. This file is the record
of that scope: what was asked, what was built, what was deliberately **not** claimed, and the
one item that still needs the owner's OPPO A3s before it can be called fixed.

State of the world when work started: 2.41/code46 published as `v2026.10.12-1`
(`CleanSweep-v2.41.apk`, SHA-256 `f728a3435f12952370759feed743431c01179b95e84ba48259bae89a35ba7a03`),
`main` at `996c69f`, nothing from this list built.

## Rules the owner set — and how each was honoured

1. **Never disable Play Protect, never rename the package, never change the signing key.**
   None of the three was touched. `applicationId` stays `com.universalrp.cleansweep`, the
   `stable` signing config and its pinned certificate are unchanged, and no part of this
   release interacts with Play Protect at all — the item 9 change is about *this repository's
   own publication record-keeping*, never about Google's scanning.
2. **Every new UI string needs English AND Tamil.** Five new user-visible strings were added
   with Tamil pairs and one retired string was removed with its pair; the catalog went
   941 → **945** keys and `tools/verify_cleaner_localization.py` passes with no duplicate keys
   and no placeholder mismatches.
3. **Do not claim a device test that did not happen.** Nothing in this release was installed on
   a phone. Every document says so in those words, and item 9's new policy *requires* the
   release notes to say it (see below).
4. **Commit messages must not contain `[publish]`.** Publication goes through the tag route
   `refs/tags/v2026.10.12-2`; no commit message in this release contains that token.
5. **Ask instead of guessing when wording is ambiguous.** Two points needed a decision rather
   than a guess; both are recorded under "Judgement calls" instead of being silently assumed.

## The 9 items

### 1 — OPPO A3s shows `0.00 W / -0.00 A` while charging (regression from 2.40/2.41)

Owner's words: *"At oppo a3s already watt reading worked perfectly, after 2.40 version its not
working. It's perfectly working on my Xiaomi Redmi 13 5G."* Their screenshot: 92 %,
"Charging / Charger (AC)", 4.36 V, **−0.00 A**, **0.00 W**, "Trickle / full" — while the same
phone's charge-history cards recorded real sessions at 4.6 W average and 4.8 W peak.

Diagnosis confirmed in source: `data/DeviceHealth.kt` read the current **only** from
`BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`. Older and OEM kernels (the A3s is Android
8.1/ColorOS) answer that property with `0` or "unsupported" while the battery broadcast extra
the broadcast's `current_now` extra still carries the real charge current. That extra was not
read anywhere in the app. (Android only exposed the key as `BatteryManager.EXTRA_CURRENT_NOW` in
**API 34**; with minSdk 26 the literal key is used deliberately, because referencing the newer
constant would trip NewApi lint on API 26-33 even though String constants are inlined.) `BatteryValuePolicy.microAmpsToAmps(0)` returns `0f`, so
`ChargingHonestyPolicy.batterySideWatts` computed `4.36 V × 0 A = 0.00 W` — a *measured-looking*
zero produced by a sensor that reported nothing.

Built:

- `data/BatteryValuePolicy.currentReading(propertyMicroAmps, broadcastMicroAmps)` is now the one
  place that decides which current reading may be used, and it returns the value **plus its
  source** (`PROPERTY`, `BROADCAST`, `NONE`). Order of trust: a real non-zero property value;
  then a real non-zero broadcast value; then a broadcast **zero** (a genuine trickle/full
  measurement from the same healthd path that supplies the voltage and temperature shown next
  to it); then nothing.
- A bare property `0` with no broadcast corroboration is **not** reported as 0 A. On those
  kernels `0` means "unsupported", and an unsupported sensor must never be rendered as
  `0.00 W` — that rule from 2.41 is preserved, not relaxed.
- `data/DeviceHealth.kt` reads `current_now` (and, symmetrically, `current_average`) from the
  sticky battery broadcast and stores `currentSource` on `BatteryReading`. When the broadcast
  rescued the reading, the existing sign note says so in the owner's language (new EN/TA string).
- **This is a fallback, not a promise.** If the A3s kernel puts no current in the broadcast
  either, the reading becomes `NONE` and the screen says "not reported" — which is still
  strictly better than printing a measured-looking `0.00 W`. The owner's screenshot is what
  decides which of the two happened; neither outcome is claimed in advance.
- `BatteryValuePolicy.microAmpsToAmps` remains the **single unit decider** — both inputs are
  microamps and magnitude is never treated as evidence of a different unit, exactly as that
  file's own comment requires. `ChargingHonestyPolicy` remains the **single watts decider**; no
  second watts computation was introduced anywhere.
- Regression proof tied to the owner's own evidence: `BatteryValuePolicyTest` asserts that
  4.36 V with a `0` property and a 1 100 000 µA broadcast yields **4.80 W** — inside the
  4.6–4.8 W range the A3s charge history already recorded — and that every unsupported
  combination still yields `null`, never `"0.00 W"`.

**Owner's device test, 12 October 2026 — result: not fixed by 2.42, fixed honestly in 2.43.**
The owner installed 2.42 on **both** phones with Play Protect ON (recorded in
[`live-guard-2.42-device-evidence.md`](live-guard-2.42-device-evidence.md)) and checked the A3s
while charging: the card **still showed 0.00 W**. That is diagnostic — the A3s kernel answers
zero through *both* the current property and the battery broadcast extra, so there was no current
to rescue, and 2.42's "broadcast zero is a measured trickle" branch reprinted the fake number.
A zero from both sources is indistinguishable from an unsupported sensor, so **2.43 / code 48
makes a zero never a measurement**: non-zero property, else non-zero broadcast, else *not
reported*. The Redmi and any phone with a working counter are unchanged, because a real trickle
arrives as a small non-zero value. No app can show real watts on a kernel that reports no current
through either Android path; what it can do is stop pretending. One A3s screenshot of the new
"not reported" wording is still owed — see
[`live-guard-2.42-device-test-plan.md`](live-guard-2.42-device-test-plan.md).

### 2 — Voice A / Voice B chosen automatically, no error message

Owner's words: *"the offline device voice selection menu just choose default one for A and B,
don't show error."*

- `MainViewModel.speakAnswer` no longer answers with *"Choose and preview two voices for this
  answer language in Voice & daily watch."* That string was removed from `data/Lang.kt`
  together with its Tamil pair, so it cannot be reintroduced by accident.
- `AnswerVoicePolicy.automaticPair` takes the engine's **real** inventory (already ordered
  offline-first, then by name) and picks the first two usable voices; with one usable voice it
  fills both slots; with none it picks nothing and invents nothing.
- `AnswerVoicePolicy.effectivePair` keeps a saved choice, replaces a saved voice the engine no
  longer reports, and treats an *empty* inventory as an uninitialised engine rather than as
  evidence against the saved voices.
- Tamil and English are stored separately, as before. `AnswerVoicePair.configured` now means
  "both slots hold a voice"; the separate `distinct` preserves the rule that a **manual** save
  still needs a completed preview and two different voices.
- The card in Voice & daily watch stays, and now states that A/B are chosen automatically and
  can be changed or previewed there. With a single installed voice it says plainly that both
  slots use it and that alternation is not possible yet.
- When the engine reports no usable voice for the answer's language, nothing is spoken and
  **no error is shown**: `Announcer` records the real delivery stage in the voice diagnostics,
  which is where a missing voice has always been reported honestly.

### 3 — The ongoing status-bar meter comes back ON automatically after an update

Owner's words: *"after update turn on automatically status bar led meter."*

- `MonitorDefaultsPolicy` is now the single owner of these defaults: `CHARGE_CARD = true`,
  `PERSISTENT_MONITOR = true`, `MALWARE_INCLUDE_SYSTEM = true`, `STATUS_PILL = false`.
- The ongoing charging card default flipped to ON in all three places that read it — the
  DataStore flow (`SettingsRepo.chargeMonitor`), the plain-prefs mirror read by a service or
  receiver (`ChargeMonitorService.cardEnabled`) and the in-memory UI state. They used to carry
  three separate literal `false` values, which is precisely why the meter stayed dark after an
  update: nothing had ever been written, so the boot receiver read `false`.
- `notify/BootReceiver` now re-arms through `MonitorDefaultsPolicy.rearmAfterUpdateOrBoot` on
  both `MY_PACKAGE_REPLACED` and `BOOT_COMPLETED`, so an update or a reboot restores the card
  even when the preference was never written. A saved OFF still arrives as `false` and is
  honoured — the switch stays and an OFF choice is never overwritten.
- **The floating pill deliberately stays opt-in.** Android requires "Display over other apps"
  before anything can be drawn beside the clock, and that access is never granted
  automatically, so defaulting it ON would promise a reading the phone has not allowed. See
  "Judgement calls".

### 4 — Background battery-health monitoring ON by default

Owner's words: *"background battery health monitoring function default turn on."*

- `PERSISTENT_MONITOR` defaults ON in the same three places, and `BootReceiver` re-arms the
  hourly watch. `MonitorDefaultsPolicy.healthWatchWanted` now counts the battery-health
  monitor's own switch, not only voice and the daily check, so the watch is armed after an
  update without the owner opening Voice & daily watch.
- The switch stays, and the honest wording stays with it: Settings now says *"On by default.
  Turning one off is your choice and is saved; it is not a failure."*, the reviewed-setup entry
  says OEM power savers and an Android force-stop still apply, and `data/CompatibilityCollector`
  reports the actual armed state of both switches.
- `daily_ai` (automatic **online** daily summaries) is untouched and remains a separate
  explicit consent, default OFF. Turning on local background monitoring is not consent to send
  report facts to a provider.

### 5 — Security check: "Include preinstalled system apps" ON by default

Owner's words: *"in security check menu include pre installed system application button turn on
default."*

- The button carrying exactly that label belongs to the file-hash check inside the Security
  screen (`state.malwareIncludeSystem`), and it defaulted to **false**. It now defaults ON
  through `MonitorDefaultsPolicy.MALWARE_INCLUDE_SYSTEM`. The switch stays so the sweep can be
  narrowed again.
- The security *review* scope (`securityIncludeSystemApps`) and the app-cache scope
  (`includeSystemApps`) already defaulted ON; both now read the same policy object instead of
  their own literals, so the three cannot drift apart.
- The honest note is unchanged and is repeated in the reviewed-setup entry: system apps are
  included **for inspection only**, no permission is granted and no app is removed
  automatically, and an unchecked or unknown hash proves nothing. A hit on a read-only
  preinstalled partition is information, not an available action.

### 6 — "Manage" opened the wrong permission window

Owner's words: *"the menu application permission fix, its opening other permission instead of
application permission wrong window for the correct fix."* Screenshot: from Security check, on
the iMobile SMS-permission item, **Manage** opened MIUI's app-level "Other permissions" hub
instead of that app's permission page.

Two causes, both fixed in `data/SecuritySettingsRoutes.kt`:

1. **Order.** The generic all-permissions action `android.intent.action.MANAGE_APP_PERMISSIONS`
   was fired first. On MIUI/HyperOS that action is claimed by the security centre, which shows
   its own "Other permissions" hub — a *different* page listing MIUI extras such as autostart
   and pop-up windows, not the app's Android permissions. The new `appPermissionRoutes` puts
   the ROM's own **package-scoped** editor first on a Xiaomi, then the AOSP editor for the
   exact permission, then the AOSP per-app permission list, then App info as the guaranteed
   last resort.
2. **Scope.** The package travelled only as the `android.intent.extra.PACKAGE_NAME` extra and
   never as the intent's `package:` data URI. Every app-permission candidate now carries
   **both**, because ROMs differ in which one they read; sending only the extra is what let a
   ROM show an unscoped hub.

Also added: `PERMISSION_EDITOR` (`android.intent.action.MANAGE_APP_PERMISSION`) with
`EXTRA_PERMISSION`, so the exact SMS permission is targeted before the app's whole list; and
`isColoros` recognition for the owner's OPPO A3s, with its own manual path in
`deviceGuidance` and in the finding's instructions. **No OEM component name was invented**:
ColorOS keeps the AOSP permission editor reachable, so its route *is* the AOSP one, and a
made-up activity would only waste a fallback step before App info. A candidate a ROM does not
expose simply fails to resolve and the launcher tries the next one, ending in the existing
honest manual-path text when the ROM refuses everything.

Routing is per-ROM and per-release, so **this needs checking on both the Xiaomi and the OPPO**.

### 7 — Main first screen: spacing, no clipping, professional layout

Owner's words: *"more gap in main first window fix it, everywhere use space and text excellent
method, dont show ugly and non professional view."* Screenshot: the top card clipped under the
header, rows crowding each other.

- Root cause found in source: the home `LazyColumn` had
  `contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)` — **no top
  inset at all**, so the first card was drawn flush against the header and the LED strip above
  it. The screen also carried four hand-picked edges (14 / 16 / 14 / 16 dp), a 6 dp header
  inset and card padding mixed at 12 / 14 / 16 dp.
- `ui/HomeLayoutPolicy` is now one spacing scale on a 4 dp base: one screen edge (16), one
  card gap (16, raised from 14 so rows no longer crowd), a real list top inset (12), a bottom
  inset (28), two card-padding sizes only (16 standard / 12 compact), one header inset and one
  icon gap. `onScale`/`scaled` let a one-off number be corrected instead of shipped, and
  `isCoherent` asserts the gaps relate sensibly.
- `ui/HomeScreen` now takes every edge, gap and card padding from that policy; the two 14 dp
  card paddings became 16 dp so the screen has one rhythm.
- **Honest limit.** This is a *layout* contract, not a rendering certificate. `HomeLayoutPolicyTest`
  can prove the top inset is non-zero and that every value is on the scale; it cannot prove
  nothing clips at a large font scale, on a small screen or in split screen. That still needs
  the owner's phones. Typography was deliberately left alone rather than "improved" blind.

### 8 — Language mismatch: some cards rendered the opposite language

Observed on the owner's 2.41 screenshots: with the header in English, "Reviewed feature setup"
and "Live Guard readings" rendered in Tamil; with the header in Tamil, "App updates",
"Reviewed feature setup", "Live Guard readings" and "Estimated phone age" rendered in English;
and *"Verified in-app download; Android confirms installation."* stayed English in both.

Diagnosis — two independent causes, both in source:

1. **Two sources of truth.** `tr()`/`Lang.t()` read the process-wide `Lang.current`, while
   several composables read `state.lang` from the ViewModel, and nothing kept them equal. On a
   cold start `Lang.current` begins as English and is only corrected when the settings flow
   emits, so the first frame could be composed in the wrong language.
2. **Compose skipped the cards that observe nothing.** `LiveGuardUpdateCard()` takes no
   parameters and observes only the update StateFlow, so on a language toggle Compose skipped
   it and it kept text composed against the old language — which is why that one line stayed
   English in *both* directions.

**Correction to the work list.** The list said the Tamil pair for *"Verified in-app download;
Android confirms installation."* was missing from `data/Lang.kt`. It is **not** missing — it has
been in the catalog since the update card shipped, and `LanguageSourcePolicyTest` now asserts
that this key and the four other cards named in the report all have real Tamil-script values.
Adding a second pair would have failed the localization gate as a duplicate key. The line
rendered English for cause 2 above, not for a missing translation; the diagnosis is recorded
here so nobody "adds" it again.

Built:

- `data/LanguageSourcePolicy` documents and enforces one authority: a saved choice beats a
  process default; `Lang.current` is the authority for every string; `UiState.lang` is only a
  mirror whose remaining job is to make Compose recompose.
- `MainActivity.onCreate` resolves the language from the saved mirror **before** `setContent`,
  and `MainViewModel.init` aligns the state mirror with the authority before the first frame.
- `AppRoot` wraps the screen tree in `key(state.lang)`, so a toggle rebuilds every screen and
  every `tr()` re-reads the authority — including the cards that observe no state at all.
  Re-selecting the same language does not rebuild anything (`requiresRebuild`).
- Every display site that chose a language was moved off the mirror and onto the authority:
  `HomeScreen` (toggle button, phone-age label — the owner's estimate stays exactly where it
  is), `SettingsScreen` (language selector), `PhoneScreens`, `VoiceScreen`, `AssistantScreen`
  and `LiveGuardMetricsMatrix`. `state.lang` now appears in exactly one production place, the
  `key()` in `AppRoot`, and the source contract fails the build if a display site reads it
  again.

### 9 — Process rule: device verification only when a release adds a dangerous permission

Owner's words: *"now from further no need to verify everytime instalation verification untill
dager permission we add to the application so fix build and publish."* An explicit owner
decision that supersedes the earlier rule requiring a recorded physical-device install for
every release.

- The gate in `.github/workflows/build-apk.yml` (the block marked *"DELIBERATE: do not delete
  or weaken this block to publish"*) now requires
  `docs/live-guard-<version>-device-evidence.md` **only when this build adds a dangerous
  permission** compared with the previously published release APK.
- The comparison is made on **built artifacts**, not on the source manifest, because only the
  merged manifest shows what the phone is actually asked to grant. `aapt2 dump badging` reads
  both the candidate APK and the CleanSweep APK published under `releases/latest-tag.txt`, and
  the new `tools/verify_release_permission_delta.py` decides.
- That tool classifies permissions correctly: appop/signature-level declarations such as
  `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`,
  `REQUEST_INSTALL_PACKAGES` and `QUERY_ALL_PACKAGES` are **not** runtime prompts and are not
  counted, so an unchanged declaration can never be mistaken for a new dangerous permission.
  Its 8 unit tests run in CI.
- **Fail closed.** If the previous release cannot be downloaded or read, or its badging lists
  no permission, the answer is "evidence required", never "assume nothing was added". Anything
  other than an explicit `no` from the tool is treated as required.
- **Every other honesty rule is unchanged.** When a device-evidence file *does* exist the
  workflow still verifies the Play Protect and installer wording, still reads
  `TESTED-APK-SHA256`/`TESTED-RUN-ID`, still publishes only a rebuild that reproduces the
  recorded digest byte-for-byte, and still otherwise promotes the recorded run's verified
  artifact — so a rebuilt digest can never inherit an earlier device test. The 2.40/2.41
  evidence records and the Play Protect install-block advisory are untouched, as are all
  previously published assets.
- The new policy is written down in [`AGENTS.md`](../AGENTS.md),
  [`PUBLISHING.md`](../PUBLISHING.md), [`CHANGELOG.md`](../CHANGELOG.md) and this release's
  notes, with the reason, as the owner asked.

## Judgement calls (recorded, not guessed)

- **"status bar led meter" = the ongoing charging card, not the floating pill.** Item 3 named
  `PILL_PREFS`, `PERSISTENT_KEY` and `cardEnabled`, and said "default the ongoing card/meter to
  ON". The floating pill was left opt-in on purpose: it needs "Display over other apps", which
  is never granted automatically, so defaulting it ON would show nothing while claiming a
  reading. If the owner meant the pill as well, that is a one-line change to
  `MonitorDefaultsPolicy.STATUS_PILL` plus the overlay-permission explanation — say so and it
  will be done in the next version, not silently assumed here.
- **The retired Tamil pair.** Item 8 asked for a Tamil pair that already exists; adding it
  would have broken the duplicate-key gate. The real cause was fixed instead, and the
  correction is recorded above rather than papered over.

## Verification status of this release

Static gates run in this workspace (no JDK, no Gradle, no Android SDK, no phone here):

- `tools/verify_cleaner_localization.py` — **945** EN/TA catalog keys, no duplicates, no
  placeholder mismatches, no 24-hour owned UI clock.
- `tools/verify_cleaner_core_manifest.py` — no retired notification-reader capability in the
  manifest, production sources or build scripts; the 2.39 archive stays reference-only.
- `tools/verify_cleaner_next_update_source.py` — manifest, updater, rating, widget, scope and
  the new 2.42 contracts; **39 required suites / 349 prepared declarations**; candidate
  2.42/code47/API26.
- `tools/tests` — 16 unit tests, including the 8 for the new permission-delta gate.
- The publication gate itself was dry-run against simulated `aapt2` badging for three cases:
  unchanged permissions → evidence **not** required; an added `READ_SMS` → required; previous
  release unreadable → required (fail closed).

What that is **not**: Kotlin/Compose compilation, resource linking, executed JVM tests, APK
signing, device delivery or a released build. GitHub Actions does the real compile, runs all
349 required cases in **both** the debug and release variants, signs and verifies the APKs.

**No byte of 2.42 was installed on a phone before publication**, which item 9's new policy
permits because no dangerous permission was added. That is stated in the release notes and in
the changelog, and it is the reason item 1's OPPO A3s result and item 6's per-ROM routing are
listed as *open* device work in
[`live-guard-2.42-device-test-plan.md`](live-guard-2.42-device-test-plan.md) rather than as
fixes proven on hardware.
