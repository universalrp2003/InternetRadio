# Live Guard 2.37 — required device checks

Status: **not run on a device**. Android 8/API26 is the minimum compatibility target,
not certification of every manufacturer. Known owner context: Redmi 13 5G,
Android16/API36 screenshots, exact MIUI/HyperOS/custom ROM unspecified; manual speech
is audible, automatic alerts reportedly are not. Do not infer new success from old
2.34 charging/display confirmation or a screenshot of the retained age badge.

Cloud verification: [run 38127107781](https://github.com/universalrp2003/InternetRadio/actions/runs/38127107781) succeeded for immutable source
`325576df99dc3d56ad635e5a2d229a0e468dd7da` in3m46s; release `v2026.10.11-1` is stable with six assets.
The successful gate ran **177 required cases per Cleaner variant** (125 retained + 52 new),
existing Radio tests, all-module compilation, signed APK/AAB generation, packaged
features, version 2.37/code 42, minSDK26 and non-debuggable/stable-certificate checks.
Cloud execution is not a launcher, audible speech or OEM/background test.

## 1. Upgrade and retained features

- Install the release APK over 2.36 without uninstalling; verify version 2.37/code 42,
  settings, package/signature and previously saved history/review controls remain.
- Keep Estimated phone age on **active Home**: English/Tamil, wrapping at narrow
  widths/large fonts and tap-to-explain estimate; unsupported evidence must not gain
  an invented manufacture/purchase year. Daily off-main cache remains.
- Exercise all six retained improvements using the
  [2.35 plan](live-guard-2.35-device-test-plan.md), plus
  [2.36 age plan](live-guard-2.36-device-test-plan.md).
- Check targeted Fix/Manage routes and Ask AI about **all current issues** remain
  relevant; unknown and dated observations must not become fake zero/current facts.
  No automatic revoke/uninstall/delete/review-completion action is permitted.

## 2. Diagnose the actual automatic voice failure

Record app version, model/API and the actual ROM string manually; do not upload keys,
private screenshots, prompts, audio or a raw state/preferences dump.

1. Open **Home speaker → Voice & daily watch**. Record master/category switches,
   quiet-hour start/end, earliest unplug hour, last automatic **event/stage/time**,
   available language evidence and media-volume step. Note Bluetooth/headset route
   and DND manually. These checks are not proof of audible output.
2. Use **Hear the voice** in English and Tamil UI modes. A queued toast is only
   acceptance; check real diagnostics/callbacks and whether you hear it. Tamil-only
   read must fail honestly if Tamil voice data is absent, never read Tamil through English.
3. Use **Test automatic charging voice** outside quiet hours. It must obey master,
   charging-category and quiet-hour gates, unlike the explicit manual read's quiet
   exemption. It does not generate a cable event or bypass hardware connection dedup.
4. Turn the master off, then category off, then enter quiet hours separately: test
   must record disabled/event-off/quiet-hour outcomes and remain silent. Turning a
   switch on is not a playback-success result. Re-enable only your chosen settings.
5. With Home visible, plug in outside quiet hours with card, pill and persistent
   monitor **all off**, but charging speech enabled. Registered runtime power listeners
   should schedule a bounded voice-only job independently; no permanent visual monitor
   may be silently enabled. Check listener registration diagnostics, not just voice switches.
   Then lock/background the phone: with an existing opted-in monitor or its visual
   fallback running, runtime listeners should remain owned. Without any live owner,
   Android8+ manifest power delivery may be missed/deferred before a job exists; explicitly
   record this limitation rather than claiming a cold-background guarantee. Repeat with
   the normal enabled card/pill. Record whether the engine initialized,
   reported start/completion, and whether you actually heard it, as three different facts.
6. Duplicate/reconnect: one physical connection must not repeatedly announce from
   card/service/job races. Unplug and reconnect should permit a new announcement.
   Same-connection duplicate work must not cancel speech. Delayed >2min or changed-token
   jobs must be skipped rather than announce a stale physical state.
7. Early unplug: below **90%**, after your configured earliest hour, outside quiet
   hours and category enabled → attempt one alert. At/above90%, before that hour,
   disabled or inside quiet hours → no early-unplug speech. Check observed percent,
   not a fabricated fine-scale value. Unplug also restores/stops the pill as below.
8. Wi-Fi→mobile→Wi-Fi while Home is visible and while the existing opted-in monitor
   runs: one debounced actual transition alert each; initial state/VPN/unknown transport
   must not invent a transition. With neither owner active, explain the hourly fallback
   can miss brief transitions and is not immediate; do not enable monitoring implicitly.
9. Exercise low/full/heat and permitted Wi-Fi-device/storage/health/daily reminders.
   Failed/blocked requests must not consume start-based cooldowns. Clock rollback
   must not suppress reminders indefinitely. A full/paused plugged battery is not
   “unplugged.” Heat/paused charging must not claim a diagnosed worn/broken cable.
10. Offline/no mobile data, absent offline Tamil, online engine rejection/error/no
    callback, slow initialization, cancellation and rapid manual/automatic reads:
    require an honest result, bounded queue/timeout and at most one installed offline
    retry in the same language. No offline-support guarantee and no mixed-language flush.
11. Kill/force-stop, Doze, battery saver and Redmi background restrictions: record
    limitations. Force-stop prevents background delivery. TTS engine completion with
    no audible sound warrants checking routing/volume/OEM policy, not a claim the
    problem is solved. The public TTS-settings route must be guarded; fallback is
    manual guidance rather than opening an unrelated settings page.
12. “Louder” must not change media-volume/DND/Bluetooth settings or claim to override
    silent mode. Prefer existing relevant OEM instructions; no blanket “allow everything.”

## 3. News freshness, no repetition or source ribbon

- Online: every item must have an actual publication year/time/offset in **today's
  phone-local day**, not fetch time. Compare opened articles to metadata manually.
  No old, missing-date, future, update-only or English/static safety filler in the Tamil banner.
  Live endpoint availability/reporting is not guaranteed by compilation or parser tests.
- Banner contains headline only, no publisher/category ribbon. Identified publisher
  suffixes may be removed; arbitrary headline text must not be erased. Review dialog
  has publication time and explicit read/open actions; source/URL evidence stays internal.
- Two publishers/tracking-URL variants/same normalized title: one item. Let unseen
  items rotate every12s until **caught up**; no endless replay. Navigate away/back,
  refresh and restart process the same local day: shown titles remain seen. Explicit
  manual review can revisit a title; it must not restart an infinite automatic loop.
- Refresh while a title becomes seen: completion must not restore a stale seen set.
  Offline/failure preserves only still-today cached items and seen state. No today cache:
  explicit offline/empty/feed-unavailable state, never old-news or a fabricated fallback.
- Leave Home open across midnight, pause, sleep/resume, change timezone/date, and
  force a refresh spanning midnight: remove no-longer-today items and update day-scoped
  seen state. Check cached/future filtering against the changed local clock.
- Malformed, oversize, entity/DTD and stalled feeds: no unsafe XML expansion, main-thread
  parsing/networking or indefinite deliberate per-feed loops. Shared budget/socket
  limits are best effort; Android DNS/networking can still delay I/O.
- Hide in review, show again in existing settings; refresh/pause/manual read remain usable.

## 4. Assistant identity/evidence

- Ask **what ai api is this**, who are you, model/provider questions, including supported
  Tamil equivalents. Check public configured labels, local/no-search metadata and no
  Wikipedia/OpenAI evidence for a configured Gemini identity. No credentials/API URL exposed.
- Selected model name must not certify that model exists, ran or answered. Free mode
  explains endpoint switching; replies carry their own provider metadata. Test offline/
  online modes and independent AI answer language (auto/English/Tamil) with different Home UI.
- A general “which AI API is best for image generation?” must remain a general question,
  not incorrectly routed to a local identity reply. Try normal officeholder questions.
- Other snippets should overlap significant query/title terms, use unique canonical URLs
  and collapsed evidence UI. A no-relevant-result lookup must not keep unrelated filler.
  Provider/web claims remain unverified advice; never execute model-returned actions.

## 5. Launcher widget, precision and separate floating pill

- Add two launcher widgets: default **large % + battery-side W**, target4×4. Cycle one
  through W/%/both; the other must retain its own mode. Remove/re-add cleans up stale mode.
- Resize width/height, rotate, switch English/Tamil, increase fonts and compare launcher
  variants. No clipped real fine-resolution percent; narrow fine readings should stack.
  Secondary rows hide at compact heights. Check readable percent, labels, progress,
  status/time, available temp/signed current/voltage/data/storage and dated saved brief.
  A brief without a timestamp must not be presented as a current security assessment.
- Clean opens the existing review/scanner (never automatic deletion), Details the health
  screen, Refresh obtains a new snapshot, mode changes promptly. Two IDs/actions must
  not alias a PendingIntent. Rapid state updates/resizing/taps must not lose the last view.
- Ordinary battery level/scale100 → integer%. If genuine finer scale is available,
  show its supported resolution up to five decimals; never invent77.45678%, smooth
  between broadcasts or derive SoC from an estimated capacity/charge counter.
- Unknown/sentinel/implausible readings must be unavailable, not0. Only actively
  charging valid current×voltage gives W; full/paused/unplugged is `— W`. Two watt
  decimals are formatting, not adapter calibration. Check microamp conversion at low
  current against raw API values; no automatic µA→mA magnitude guess. Vendor units,
  signed-current conventions and calibration may still differ.
- Confirm no new permanent widget refresh service; stopped-app launcher updates are
  Android-controlled (periodic target30min, jobs/active existing monitor/Refresh can
  update sooner), not guaranteed five-second always-live measurements.
- Floating pill must remain **watts only while connected**. With background monitoring,
  unplug restores normal data/network view; charging-only mode stops. No enlarged home
  widget layout/view-setting leak into the pill. Existing data accounting continues.
- With opt-in charging history, a raw20.x/80.x level must not become an “exact observed
  20→80” interval through integer rounding. Actual whole boundary samples/legacy integer
  readings remain supported; gaps/reboots/unsupported fraction samples remain partial.

## Coverage matrix / remaining limits

At minimum: owner's Redmi13 5G/API36, another OEM Android12+, and Android8/API26
emulator/device for guards, with/without usage access, notifications, overlay and
Tamil/offline voice data. Test locked/background/force-stop states and multiple launchers.
No result is claimed until it is recorded. Do not upload private recordings/screenshots
or selected karaoke audio without explicit permission.
