# Standing project requirements

- Actively maintain CleanSweep, Ramesh Radio and PulseEQ. Preserve the two legacy
  apps, AppForge and the original Internet Radio; do not delete them.
- Target Android 8.0/API 26 and newer across all manufacturers, not one user's phone.
  Use runtime guards/fallbacks and honestly label device-dependent capabilities.
- Following the owner's 2026-10-08 instruction, prepare README, changelogs and
  release notes for future app updates; publish the verified release APKs for the
  three maintained apps to GitHub Releases rather than leaving only CI artifacts.
  Do not publish failed builds, mislabel incomplete features or overwrite an
  existing release asset with different code under the same version.
- Include an honest validation summary and identify required real-device tests.
- Never upload selected audio for karaoke without explicit approval; online and
  offline AI separation are still unfinished (see docs/radio-karaoke-investigation.md).
- Preserve the owner's phone-age estimate on the Home dashboard. On 2026-10-11
  the owner explicitly requested its restoration and asked that future redesigns
  not silently remove it. Keep it labelled as an estimate, not an exact manufacture/
  purchase date; unsupported model/factory evidence must remain unavailable.

- Following the owner's 2026-10-11 voice/news/widget choices: redesign the **launcher
  home-screen widget**, not the floating pill; latest compact Battery/Data/Phone
  profiles supersede the former large battery/watts layout. Keep floating
  watts-only charging/unplug behaviour intact. Never invent five-decimal battery
  precision or turn display decimal places into an accuracy claim.
- Home news must be **today only in the phone's local timezone**, real publication
  timestamps required, deduplicated and not automatically repeating read headlines.
  No publisher ribbon/static safety tips/synthetic "today" fallback in the banner.
  Retain article links/source metadata internally; keep security review separate.
- The owner reports manual speech works but automatic alerts are silent on Redmi
  13 5G (Android16/API36 screenshots, exact ROM unknown). Diagnose delivery gates,
  callbacks/background paths honestly; never claim enabled switches or TTS engine
  completion certify audible speech/OEM background survival.

- Automatic power delivery cannot rely solely on Android8+ manifest implicit broadcasts.
  Runtime receivers must be owner-scoped to the visible app or existing enabled monitor/
  visual fallback, with honest registration evidence and no cold-background guarantee
  when no owner runs. Do not silently enable a persistent service to mask this limitation.

- Owner explicitly lifted the source-only hold on 11 October 2026: **build the next
  version and publish after verification**. Prepare Live Guard2.39/code44; execute
  all required tests/build/package/signing gates before publication. Never claim a
  failed or untested candidate is a verified released APK.
- **2026-10-11 owner decision (option 1, already answered — do not re-ask):** after
  Play Protect blocked installation, prepare the main Live Guard app **without
  notification/message reading**. Remove the capability from production completely —
  never merely switch it off, hide the screen, disable a component or leave a hidden
  activation path. Keep every other feature. The 2.39 reader sources are archived in
  `docs/archived-notification-reader-2.39/` as reference only: not a Gradle source
  set, not a flavor/module, never distributed as a workaround. Any future redesign
  needs a fresh owner decision plus policy and device review.
- Do **not** disable or bypass Play Protect, conceal the installation source, rename
  the package or change the signing identity, and do not call the removal of one
  capability a guaranteed fix. The blocked APK hash is unconfirmed from the
  screenshot. Update the public installation advisory
  (`docs/live-guard-play-protect-install-block.md`) before recommending any APK, and
  never erase historical CI results or replace older published asset bytes.
- Use correct corresponding Tamil/English throughout Live Guard, its launcher
  widget and notification text; notifications follow the selected app language.
  Human-visible clock times use **12-hour AM/PM**; preserve machine/feed parsing formats.
- Bound and cancel assistant requests; show progress/errors, discard superseded
  results, and do not invent live-web success/current prices. Exact Redmi13 5G
  evidence must not be replaced by Redmi12/13C/4G Wikipedia prices.
- Confirmed answer voice choice: **alternate two selected/previewed voices BETWEEN
  answers, reading each answer ONCE**. Do not re-ask that choice or infer gender
  from Android voice metadata. Missing suitable voices must be honestly reported.
- Preserve the genuine last completed permission-review date across navigation/
  restart. A saved dated summary is not a fresh check or a safety verdict; do not
  fabricate a review date just by opening the app. Keep unknown/incomplete states
  and opt-in package-specific history distinct.

- **2026-10-12 owner request (Live Guard 2.41/code46):** prepare the next candidate with
  four changes only — (1) honest watts/amps (battery-side, measured-only, labelled, no
  amp-for-watt substitution), (2) a screen-on/off charging note, (3) a bounded AI output
  budget plus a provider model check, (4) one owner-set **44 °C** battery heat warning with
  an honest sensor name and a hush window. The owner did **not** authorize publication:
  2.41 is published only after the owner installs the exact candidate APK on his phone and
  the real Play Protect wording plus installer outcome are recorded with the tested digest
  and run id in `docs/live-guard-2.41-device-evidence.md`. **That happened on 12 October
  2026:** the owner installed the candidate (Play Protect "App scan recommended" → "Scanning…"
  → "This app looks safe"), and 2.41 was published as `v2026.10.12-1` from the tested bytes in
  run 38202739572. Never publish a rebuilt digest as if the device test covered it; keep 2.40's published assets, the 2.39 history and the
  install-block advisory intact, and keep `releases/latest-tag.txt` at `v2026.10.11-4` until
  a real publication happens.

- **2026-10-12 owner decision (Live Guard 2.42/code47) — publication rule changed:** from now
  on a release does **not** need a recorded physical-device install unless it **adds a dangerous
  permission**. Owner's words: *"now from further no need to verify everytime instalation
  verification untill dager permission we add to the application so fix build and publish."*
  The tag release step compares this build's merged-manifest permissions with the previously
  published release APK using `aapt2`, and `tools/verify_release_permission_delta.py` decides;
  appop/signature permissions (`MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`,
  `SYSTEM_ALERT_WINDOW`, `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`) are **not** runtime
  prompts and never count as an addition. It fails closed when the previous release cannot be
  read. **Unchanged and still enforced:** when a device-evidence file *does* exist, publication
  still publishes only the tested bytes (a rebuild reproducing the recorded SHA-256, or the
  recorded run's promoted artifact), so a rebuilt digest can never inherit an earlier device
  test; Play Protect is never disabled or bypassed; the package name and signing key never
  change; and release notes must **never** claim a device test that did not happen. A release
  published without one must say so in its own words — see `releases/v2026.10.12-2.md`.
- **2026-10-12 owner request (Live Guard 2.42/code47) — the nine-item work list:** (1) restore
  charging watts on phones whose `BATTERY_PROPERTY_CURRENT_NOW` reports nothing by falling back
  to the `BatteryManager.EXTRA_CURRENT` broadcast extra, keeping one unit decider and one watts
  decider and never faking `0.00 W`; (2) choose Voice A / Voice B automatically from the real
  installed inventory and never show the "choose and preview two voices" error again; (3) the
  ongoing status-bar charging meter defaults ON and is re-armed by an update or reboot even when
  the preference was never written; (4) background battery-health monitoring defaults ON, keeping
  the switch and the honest "OFF is not a failure" wording; (5) the Security screen's "Include
  preinstalled system apps" defaults ON; (6) permission findings must open **that app's own**
  permission page, not MIUI's "Other permissions" hub, with per-ROM ordering and the existing
  honest text fallback; (7) one spacing scale on the main screen and no clipping under the
  header; (8) one language source of truth, resolved before the first composition, so no card
  renders the opposite language; (9) the publication-rule change above. The **floating pill stays
  opt-in** (overlay access is never granted automatically) and automatic *online* daily summaries
  stay a separate consent. Item 1 still needs the owner's OPPO A3s screenshot and item 6 still
  needs both ROMs checked: see `docs/live-guard-2.42-device-test-plan.md`. Do not describe
  either as proven on hardware until that happens.

## Latest reviewed source batch — build/publication now authorized

Owner choices: **reviewed_setup**, **on_device_only** notification translation,
**lmarena** exact/datable ratings. Respect saved OFF choices; no silent sensitive
access, private-message upload, online message TTS fallback, APK installation or
Play Protect bypass/scan guarantee. The **compact Battery/Data/Phone launcher
profiles supersede the old large BOTH widget default**; floating pill unchanged.
Only real observed unplug events may create its timestamp/level. System app
inclusion is review/scope, never automatic selection or deletion.

New source-only helpers/screens/services and dependency declarations are NOT
compiled/verified APK behavior. Current gate: **349 required cases EACH variant,
39 suites** (2.42 added the battery-current source, automatic answer voices, monitor
defaults, language authority and home spacing suites; the retired reader's 12
historical cases still run from test sources); declaration counts are not executed
tests. See
`docs/live-guard-next-update-review.md` and run the static contracts with
`python3 tools/verify_cleaner_next_update_source.py` before any authorized build.
Owner has now explicitly authorized commit/push/CI/build and verified publication.
Until the checks succeed this remains a candidate. Browser background
or screen-off is not a guarantee of an active Arena turn surviving suspension;
saved files persist independently.

- Current authorization: build the **2.40 / code45 core candidate** (notification
  reader removed) and run every gate — but **publishing 2.40+ is gated**: the tag
  release step refuses a CleanSweep version newer than 2.39 unless
  `docs/live-guard-2.40-device-evidence.md` records the owner's real Play Protect
  and installer outcome **and quotes the exact candidate APK SHA-256**. Do not
  delete that gate, and never publish a rebuilt digest as if the device test covered
  it. *(Superseded on 2026-10-12 by the owner's 2.42 decision below: the evidence
  file is now required only when a release **adds a dangerous permission**. The
  tested-bytes rule inside the gate is unchanged, and 2.40 and 2.41 were published
  under the old, stricter rule — their records stay exactly as written.)* Required gate: **301 cases EACH Cleaner debug/release variant, 33 suites**,
  `:radio:testDebugUnitTest`, all-module APK/AAB, package/version/API26/
  non-debuggable/stable-certificate/packaged-feature/actual-size checks and the
  retired-capability source+merged-manifest+debug/release-DEX rejection. The last
  published release stays **2.39 / code44, tag v2026.10.11-3**; its assets and CI
  records are immutable. Preserve the2.37 and older assets too. Device limitations
  must remain visible.

-2.38 compiled/published successfully, then its82,630,093-byte universal APK
  exposed the updater's50MiB cap. Do NOT replace its assets. Final2.39/code44
  supersedes it:128MiB guarded cap, compressed/extracted JNI with allABIs retained,
  and CI checks actual APK fits the update cap before publication. Unchanged
  Radio/EQ companions are carried byte-for-byte from verified2.37 rather than
  changing binaries under their unchanged versions.

## Final verified publication

-2.39/code44 is verified/published as stable/latest`v2026.10.11-3`, source`6790a96b591b5a3215f5f28193726f0cd607c04f`,
  CI38143638654/job114482383017SUCCESS6m00s; all295 required tests EACH variant and APK/AAB/
  stable-certificate/API26/feature/actual-size gates passed. Actual44,610,589-byte
  universalAPK fits both128MiB and prior50MiB caps. Never replace its assets.
-2.38 was published then superseded for updater-size correction, not overwritten.
  Companion1.8/1.2 assets in2.39 match verified2.37 bytes/digests exactly. Older
  assets preserved. Metadata/notes/README should identify actual compiled source,
  not a later documentation commit. Phone/native/voice/notification tests remain
  unperformed. Any further code change needs another unique version/tag.
