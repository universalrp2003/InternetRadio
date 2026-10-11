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

## Latest reviewed source batch — build/publication now authorized

Owner choices: **reviewed_setup**, **on_device_only** notification translation,
**lmarena** exact/datable ratings. Respect saved OFF choices; no silent sensitive
access, private-message upload, online message TTS fallback, APK installation or
Play Protect bypass/scan guarantee. The **compact Battery/Data/Phone launcher
profiles supersede the old large BOTH widget default**; floating pill unchanged.
Only real observed unplug events may create its timestamp/level. System app
inclusion is review/scope, never automatic selection or deletion.

New source-only helpers/screens/services and dependency declarations are NOT
compiled/verified APK behavior. Future gate: **295 required cases EACH variant,
32 suites**; declaration counts are not executed tests. See
`docs/live-guard-next-update-review.md` and run the static contracts with
`python3 tools/verify_cleaner_next_update_source.py` before any authorized build.
Owner has now explicitly authorized commit/push/CI/build and verified publication.
Until the checks succeed this remains a candidate. Browser background
or screen-off is not a guarantee of an active Arena turn surviving suspension;
saved files persist independently.

- Current authorization: build/release **2.39 / code44**, tag **v2026.10.11-3**.
  Release only after all295 required cases EACH variant and all-module APK/AAB,
  stable signing/API26/non-debuggable/packaged-feature checks pass. Preserve the
  immutable2.37 and older assets. Device limitations must remain visible.

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
