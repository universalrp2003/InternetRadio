# Live Guard 2.36 — phone-age restoration device test plan

**Status:** required manual checks, not performed by the coding environment.
Target Android 8/API 26+ across manufacturers. The owner's 2026-10-11 screenshot
of 2.35 shows Android 16/API 36 with the badge missing; it is not a 2.36 test pass.
The owner identified Redmi 13 5G earlier; exact MIUI/HyperOS/ROM version is still
unknown. Earlier 2.34 charging/display confirmation is limited to that release.

Record only voluntary model/Android/API/ROM/app-version information (2.36/code41).
Do not upload serials, IMEI/IMSI, keys, Wi-Fi identifiers, private app screenshots
or arbitrary raw logs. No screenshot was added to the repository for this fix.

## Phone-age badge and explanation

1. Update the installed 2.35 release to 2.36 without uninstalling. Preferences and
   local histories must remain; **Estimated phone age** must be visible by the
   **Live Guard readings** heading on active Home, not only in another card/screen.
2. Known model/factory generation: show an approximate years/months/reference-year
   value. The former “About 3 yr 4 mo (2023)” is a format/example, NOT a required
   value hard-coded for every phone. A coarse generation date may predate this
   handset's purchase/manufacture; do not validate it against ownership duration.
3. Tap: explanation must identify the model-family or factory Android-generation
   basis, approximate reference months and lack of exact manufacture/purchase/
   first-use evidence. Dismiss by Close, Back and outside tap. Dialog text scrolls
   for large-font/small-height screens. No upload or setting change occurs.
4. Missing/blocked factory properties and unrecognized hardware model: badge stays
   visible with **Not enough information**, not a made-up 2022 year or “new device”.
   Unknown future API levels/future reference dates must not become negative age.
5. OS-updated handset: current Android 16/API36/security patch does not substitute
   for initial factory API or reset the estimate. Raw model codes such as SM-S918B
   must not be substring-matched to the older S9 family.
6. Check English and Tamil, portrait/landscape, narrow ~320dp width, increased
   display size and fonts up to 200%. Badge/title/value/OS/API line must wrap and
   stay readable; no clipping, overlapping, ellipsis or loss of the age feature.
   TalkBack should expose the estimate label/value and its clickable explanation.
7. Navigate away/back, background/foreground and rotate: no Compose-thread device
   reflection work, duplicated service or crash. Estimate is cached in memory per
   local calendar day and recalculated after day change/app restart. A wrong phone
   clock can affect the result; this is not independently verified calendar age.
8. Test representative Android8/API26, Android14/API34 and upgraded Android16/API36
   devices/ROMs when available. Factory property access is best effort, not a promise
   about every manufacturer. Denied optional usage/storage/notification permissions
   must not remove the badge or make age a permission/safety score.

## Preserved regressions

Run the [2.35 six-feature plan](live-guard-2.35-device-test-plan.md) and
[2.34 charging/settings/AI plan](live-guard-2.34-device-test-plan.md) against 2.36:

- Home retains real Android/API text, timestamped/unknown storage/RAM/security/
  network readings and reachable History & tools/context links.
- Self-test explicit safe report preview; private history switches still OFF by
  default; security coverage/review protections and charging gaps/boot/threshold
  disclosures remain.
- Data-period/expiry modes/history/conditional forecasts and grounded manual
  checklist/fresh rechecks work as before. No automatic security/file changes.
- Plug/full/paused/missing sensor → watts only (`— W` unknown); unplug → prior
  normal view with background monitoring or stop in charging-only mode.
- Relevant Settings/app targets and complete fresh AI requests honour privacy;
  model/age inputs are not newly appended to AI/diagnostic exports.

## Automated evidence

The build is required to run **125 regression cases in EACH CleanSweep debug and
release variant**, with none of the required suites skipped (113 retained + 12 age).
Existing Radio tests and all-module debug/release/APK/AAB, feature, version,
API26 and project-certificate gates also apply. An active-Home source check plus
packaged badge/estimator checks guard the known removal; they do NOT prove pixels,
accessibility, OEM rendering or exact physical handset age. Record the actual CI
run/source only after success; do not describe these manual checks as completed.

**Automated publication evidence:** [GitHub Actions 38097130627](https://github.com/universalrp2003/InternetRadio/actions/runs/38097130627)
passed in **5m1s** on source `041f2ba9e84909af0ab53691f31be3810882a939`, including
all 125 required cases in each Cleaner variant, existing Radio tests, all-module
builds/APKs/AABs and feature/version/API26/non-debuggable/project-signature checks.
Stable `v2026.10.11` (2.36/code41) has all six maintained-app assets; 2.35/2.34
sources/assets remain unchanged. This does not mark any manual check above as passed.
