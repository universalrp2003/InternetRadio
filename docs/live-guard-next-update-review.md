# Live Guard2.39 — verified release and remaining device review

## Current active status (2026-10-14)

This document preserves a historical Live Guard 2.39 release review. The LM Arena / Chatbot Arena rating-card proposal below is **superseded historical material, not an active requirement**: the card and active ratings data, policy and tests have been removed from the current app. Do not restore a rating feed or substitute rating card without the owner's explicit approval. Current AI settings show provider/key-configured status, not full API keys; diagnostic output also redacts credentials in custom endpoints. This status note does not rewrite historical release records, evidence or device-review notes below.

Owner authorized build/publication on11 October2026. **2.39/code44 is now
verified/published** as stable/latest`v2026.10.11-3`, **2026-10-11T13:38:57Z**.
[CI38143638654](https://github.com/universalrp2003/InternetRadio/actions/runs/38143638654)
/job114482383017 succeeded6m00s using exact immutable source`6790a96b591b5a3215f5f28193726f0cd607c04f`;
all295 required cases EACH variant, all-module builds, API26/signature/feature
and actual APK size gates passed. Actual universal APK44,610,589 bytes, allABIs
retained. Both update caps and identical previous companion bytes were checked.
2.38 passed/published but its size-limit issue is superseded without changing its
assets.2.37 and older assets remain unchanged. Preparation-only checkpoints below
are historical, not a substitute for actual device validation.

## Confirmed owner choices

- **Reviewed setup**, not blanket enable-all: safe checks/refresh defaults, helpful reminders, explicit optional switches and Android access. Saved OFF preferences are respected. Apply writes only switches actually edited in the review, not stale initial UI defaults.
- **On-device-only notification translation**: consented model download, no message-to-AI/online translation fallback. Missing/unknown language/models means sender name only. Private notification speech also selects a reported **non-network installed TTS voice**; the engine's implementation/privacy policy still applies.
- **LM Arena / Chatbot Arena**: exact model IDs, source/category/date, not IQ/universal intelligence or execution proof. No nearest-model substitution.
- **Answer voices**: two actually selected/previewed voices alternate BETWEEN answers; each answer is spoken once. No gender inference.
- **Compact launcher widgets supersede the former large `% + W` default**. The separate floating pill's existing charging-watts behavior is unchanged.

## Integrated pending source

### Original next-update fixes retained

- Shared 45-second chat budget includes the 8-second web lookup; progress/elapsed/cancel and revision ownership discard stale results. Provider changes are explicit, not hidden chosen-provider-to-Free fallback. Checks/models have 15-second limits; report provider calls have 90-second limits; Free tries at most two endpoints without retries.
- Bounded cancellable network transport, sanitized localized errors, structured native grounding sources and exact-product/price gates. No guessed current Redmi13 5G prices or substituted Redmi12/13C/4G evidence.
- TODAY-only, phone-local, dated/deduplicated English/Tamil news; no older fallback, publisher ribbon or fabricated filler.
- Mode-correct welcomes/privacy, requested-model rather than execution-proof metadata, genuine saved permission-review aggregate/Flow/Clear and unknown/incomplete/future-clock distinctions.
- 12-hour AM/PM display helpers, saved app-language notifications, installed voice inventories/previews/per-language A/B choices, no automatic answer replay.
- Phone-age estimate and all six shipped improvements retained. Package/signing/minimum API26, Radio/PulseEQ and legacy apps remain protected.

### Reviewed setup and reminders

- Home setup card, settings routes and a feature directory explain configuration/access/optional-OFF separately. File/usage/notification/overlay/install access is not granted automatically.
- Voice master, charging foreground card, background Wi-Fi scan and permanent monitor default OFF when no choice is saved; existing saved choices are read, not overwritten. Daily safe local checks remain configurable. Optional setup notifications are OFF by default, silent, aggregate-only, at most once daily, and skip deliberately OFF/skipped entries. No new permanent reminder service.
- Automatic online daily summaries now require separate explicit consent, default OFF; allowed report facts may consume provider quota. No notification content joins AI reports.
- Startup permission popups are replaced by explained setup actions. API26–29 storage and API33+ notification permissions still use Android runtime confirmation.

### Cleaner-only browser-free APK updates

- Home Update button pulses only for eligible newer stable **published official release metadata**. Radio/PulseEQ shared controls are untouched. No unpublished CI artifact or automatic download/install.
- GitHub API check, exact official repository/tag/asset binding, published SHA-256 and size required. Streaming download has a 180-second deadline, progress/cancel, one worker/no queue, HTTPS GitHub delivery-host allowlist, redirect/size caps, private unique cache file and failure cleanup.
- Before installer handoff, recheck digest/size/package/version name/higher installed version code and the stable installed signing certificate. Stable certificate pin remains `9e30f4eab8afce5e4ab444ff9578fec354d6d1414d245253590a9703d5985d55`.
- FileProvider exposes only the private verified-update cache directory. Unknown-apps access is explained and user-controlled. Android installer confirmation/signature verification and Play Protect/scanning decisions belong to Android; cannot be silently bypassed or forced.
- Official release notes remain identified publisher text, not claimed translated/verified instructions. Metadata eligibility is not a claim that an APK has already downloaded/passed its checks.

### 2.41 candidate (code46) — honest readings, heat warning, AI budgets

- **Honest watts/amps** (`data/ChargingHonestyPolicy.kt`): the battery-side policy is the one
  implementation of "may this be called charging power" (measured V_batt × I_batt, charging
  only, 2–12 V / ≤20 A / ≤150 W plausibility, no guessed 0 W). It is wired into the battery
  reader, the notification card and the health screen; amps are shown as their own labelled
  magnitude and never in a watt's place.
- **Screen-on/off note**: card and health screen state that the phone's own draw changes the
  battery-side reading and that the reading is not the charger adapter's output.
- **AI output budget + provider model check** (`ai/AiOutputBudgetPolicy.kt`): 32–4 000 output
  tokens per ask (0 for the model list), shrinking when the request deadline is nearly spent;
  a model missing from the provider's own `/models` list is rejected with the existing
  translatable message in the connection test and after "Load models". An unavailable list is
  never treated as evidence against a model, and no model is ever silently substituted.
- **44 °C heat warning** (`data/ThermalWarningPolicy.kt`): one owner-set battery threshold
  (was 45 °C in the voice path and 42 °C in one plug-in check), honest sensor naming, a
  formatted real temperature, a 3-hour hush, and no claimed cause or diagnosis. The status
  gate fails if `Announcer.HOT_BATTERY_C` and the policy drift apart.
- **Published 12 October 2026** as `v2026.10.12-1` from the exact device-tested bytes after the
  owner's Redmi 13 5G install (Play Protect ON: "App scan recommended" → "Scanning…" → "This app
  looks safe"); record in `docs/live-guard-2.41-device-evidence.md`.

### Selective notification reader — REMOVED in the 2.40 core candidate

> **Status change (2026-10-11).** Play Protect blocked installation of the published APK
> and the owner chose option 1: prepare the main app **without** notification/message
> reading. Everything below describes the 2.38/2.39 behaviour and the review that produced
> it; it is history, not current capability. The 2.40 candidate removes the service,
> binding permission, intent filter, screen/route/links, ML Kit dependencies and
> service-label resources, and archives the sources under
> `docs/archived-notification-reader-2.39/` (reference only). Do not restore any part of it
> without a fresh owner decision plus policy and device review — see
> [`live-guard-play-protect-install-block.md`](live-guard-play-protect-install-block.md).

- New Android notification listener and explicit app/sender multi-selection. Per-app sender-only default and full-message switch; app-scoped sender-name filter can select multiple names, including manually entered names. Empty sender selection reads nobody.
- Names supplied by notifications are **not verified contact identities**. No Contacts permission or notification/message history. Selected sender choices persist privately; candidate names/fingerprints remain bounded session memory. Discards unselected apps before examining their content.
- No old-notification replay; fresh-post/message timestamps, group/ongoing/call/progress/aggregate filtering, coalescing and deduplication. OTP/password suppression is a heuristic, not a guarantee that every sensitive message is recognized.
- Default unlocked/screen-on-only; recheck access/config/language/DND/freshness before speech, cancel queued/active message speech on screen-off, disconnect or choice change. Revoking Android notification access is a separate Android action; turning reading OFF alone does not revoke access.
- ML Kit bundled local language identification `17.0.6` and translation `17.0.3`, verified against Google's official samples. English/Tamil only; unknown/unsupported languages or failures fall back to sender name only. No online TTS/AI path for private content.
- Explicit consented, Wi-Fi-default model download. SDK downloads may finish after page departure; no promise that coroutine cancellation physically cancels a model-download task. Manual offline voice preview does not enable reading, and engine completion does not prove audibility.

### Compact widget / scope / help / rating

- Three compact 2×2 launcher entries: Battery, Data, Phone; each View selector cycles the same profiles. Per-instance choices, owned-ID validation, coalesced IO refresh and 48dp controls. OS/launcher cell sizes and font scaling still require physical testing.
- Current reported battery; **last observed actual power-disconnect** time/% (never stamped by opening an already-unplugged app/widget). One clearable aggregate, separate from opt-in charging history. Missed OEM/force-stop events remain unknown or last-observed, not actual-latest claims. Phone clock anomalies are labelled. No fake percentage precision.
- Metered mobile usage/remaining only when available/user-configured allowance; no guessed default quota in the new widget. Wi-Fi or user-set unlimited5G uses storage/phone details. Floating pill remains unchanged.
- Security/cache include-system defaults ON when unset, OFF choices persist, cache refreshes each opening and cancels/discards superseded reads. Newly loaded cache rows are **not automatically selected**; refresh preserves existing explicit selection. No automatic system-data deletion or permission changes.
- Provider/VirusTotal official key/help links with nearby selected-language text/voice guides; no permanent free quotas/billing guarantees. Guides never speak keys.
- LM Arena rating card reads the official `lmarena/arena-catalog` overall-text `full` dataset, pins file contents to the actual update commit, verifies Git blob bytes and matches exact IDs locally. Selected model/API key is not sent in a lookup URL. Shows source date separately from fetch date, published interval when present and ambiguity/no-match/no-current states.
- **Verified public feed is dated 11 December 2025, not current October 2026.** It cannot substantiate a live rating for the owner's model. Older-than-seven-day/future-clock data is explicitly dated, not called current; live-site link is provided. Never invent a score for Gemini3.8/routed Free IDs or remove provider/preview/free suffixes to force a match.

### Heavy AI operation repair

- Fresh-report preparation **and** provider request now share one 90-second deadline, owned job, progress/elapsed/cancel and provider/language/navigation invalidation. Four bounded detached local-read owners prevent blocked Android/Binder work from holding the UI cancellation indefinitely; late results are dropped. Native work may finish later and cannot be forcibly killed.
- Device analysis likewise has one owned 90-second budget. Scan+AI uses a bounded local sweep and **one batch request**, not one 90-second request per device. Network-sharing OFF is honored. AI guesses stay labelled text, not automatically saved user names or verified hardware fingerprints. Previously saved labels remain, without new identity guarantees.

## Source-only verification checkpoint

- Localization validator: **929 EN/TA catalog keys** at this checkpoint, no duplicates/format-placeholder mismatches; audited explicit literal `tr()`/`t()` keys covered; owned clocks 12-hour and saved-review reads invent no date. Broader dynamic legacy text and translation meaning/layout are not fully certified.
- Kotlin lightweight parser: **151 main/test Kotlin files checked with no suspect nodes**; one unchanged previously shipped `WidgetReadingPolicy.kt` companion form excluded for a grammar-only limitation. `TamilInfoStripRepo.kt` catch whitespace was normalized in memory only for the parser. Retained shipped tests cover these files in the next actual build. Parser success is **not Kotlin type/API/Compose compilation**.
- XML well-formedness: **20** cleaner manifest/resource XML files. Not Android resource linking/rendering.
- All **22 shipped test source files** verified byte-for-byte/Git-blob-identical against actual compiled source `325576df…`. New declarations: previous62 + latest56. Required future gate: **177 retained +118 new =295 cases EACH debug and release**, **32 required suites**. At preparation time these tests were unexecuted. Both2.38 and final2.39 exact-source
  CI then executed/verified all295 required cases EACH variant with no skips.
- Whitespace/static-contract checks must be rerun after any later edits. No Java/Kotlin compiler, Android SDK, emulator or physical phone in this workspace.
- Restored local metadata was aligned to the verified remote docs commit `3b3d376…`
  only after checking400 baseline blobs (356 unchanged/44 intentional edits,0 missing)
  and preserving all452 working files byte-for-byte. No worktree reset/checkout.
  Radio/EQ and previously shipped source remain intact.

## Completed cloud validation and required remaining device validation

1. Authorization is now explicit. Final candidate2.39/code44 uses a new release tag;
   never overwrite existing2.37 assets or publish a failed candidate.
2. Completed: Kotlin/resource builds, all295 required tests in both variants,
   Radio checks, all-module APK/AAB and signature/package/API26/release/size gates.
   No lint-only all-device certification is claimed. The first SDK compile failure
   was fixed before publication; the size correction uses a new version/tag.
3. Redmi13 5G / Android16 actual ROM: both UI languages/font scales, today news/date boundaries, exact-product evidence/no-price state, provider timeout/cancel/model retirement/permission changes and source metadata. Manual vs automatic audible tests with two saved installed voices; callbacks/registration are not proof of heard speech or OEM survival.
4. Notification reader: consent/OFF, several apps/senders, duplicate titles/groups/summaries/reposts, unsupported/short languages, model failures/no-network TTS, sensitive-code redaction, muted media/quiet/DND, screen lock mid-queue/utterance, revoked access, process restart/force-stop and rapid config/language changes. Verify no content in app storage/logs/AI requests.
5. APK updater: current/no-new/missing digest/rate-limit/offline, slow/cancelled/redirect downloads, wrong package/version/size/hash/signature/installed certificate, private URI grants/cache eviction, permission-denied/returned, installer cancellation/failure/success. Do not claim a forced Play Protect scan.
6. Launcher compact presets, resize/smallest cells/large fonts, profile isolation/pending intents, Wi-Fi/unlimited/metered/missing-plan/partial usage, real unplug/replug/rapid events/clock changes/reboot and missing-event unknown states. Floating pill unchanged.
7. Setup saved OFF/default/migration/permission-denied/reminder cadence and cache reopen/revocation/scope races/no auto-selection/deletion. Rating exact/no-match/routed/malformed/stale/clock/network cases; do not treat the December2025 feed as current.
8. Future successful release requires verified CI/release notes/changelog/README/immutable assets and explicit device limitations. Until then, published2.37 stays the verified build.

## Arena/Brave background question

Saved repository changes persist independently of keeping the page foreground. An **active Agent turn** surviving browser background, screen-off, Android/OEM or platform suspension is not guaranteed. No invented keep-alive setting and no promise of autonomous work continuing indefinitely after a reply. The owner has now requested cloud build/publication; the monitored CI run, not
browser visibility, determines its completion. Never promise an active Arena turn
or autonomous follow-up survives platform suspension.
