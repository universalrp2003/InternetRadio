# Changelog — maintained Android apps

## Live Guard 2.40/code45 — PREPARED CANDIDATE: notification/message reading removed

**Not published and not device-tested.** After Google Play Protect blocked installation of
the published Live Guard APK ("App blocked to protect your device"), the owner chose to
prepare the main app **without notification/message reading**. The screenshot shows no
hash and Google gave no hash-linked verdict, so the blocked artifact is unconfirmed and the
2.38/2.39 reader is the leading suspect, not a proven cause; removing it is not a
guaranteed clearance.

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
- Gates before publication: **301 cases in each** Cleaner debug/release variant
  (**33 suites**), `:radio:testDebugUnitTest`, all-module APK/AAB and
  package/version/API26/non-debuggable/stable-signature/packaged-feature checks, the
  retired-capability/DEX gate and measured APK size (128MiB cap plus the existing 2.39
  client's 50MiB bound). Publication additionally requires the owner's recorded Play
  Protect + installer outcome for the exact APK SHA-256 in
  `docs/live-guard-2.40-device-evidence.md`; the workflow refuses a newer CleanSweep
  version without it. Previous release assets and CI records are never overwritten or erased.

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
