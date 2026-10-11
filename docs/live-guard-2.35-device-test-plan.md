# Live Guard 2.35 — six-feature real-device validation plan

**Status:** required manual checks, not executed by the coding environment.
Android 8/API 26+ is the compatibility target. The owner confirmed 2.34 charging/
unplug display on Redmi 13 5G only; that is not evidence that 2.35 history, self-test,
timeline, forecasts or AI checklist have run successfully. No firmware version was
provided. Keep the [2.34 plug/unplug/route test plan](live-guard-2.34-device-test-plan.md)
active as a regression checklist.

## Record test context (voluntary, no identifiers)

- Model/manufacturer; Android version/API; MIUI/HyperOS/other ROM version entered
  manually (not inferred from model); app version 2.35 / code 40.
- Charging cable/adapter circumstances, battery level, ambient conditions, screen
  use, battery-temperature/current availability, persistent-monitor and pill switches.
- Granted optional permissions. Never post keys, serials, IMEI/IMSI, Wi-Fi details,
  screenshots of private apps, messages, files or full arbitrary logs/prompts.
- Prefer the self-test preview export; it deliberately excludes private identifiers
  and raw state. It still identifies model/OS and permission/configuration status.
  Share it only when comfortable.

## 1. Truthful dashboard

1. Fresh app install or cleared app data: permission review says **Not checked**,
   not 95/100 or Safe. Run a check and verify its date.
2. Block the fixed TCP latency endpoint or disconnect the network: missing latency/
   jitter is **Not measured**, no fabricated 35/50 ms or good-quality grade.
   A blocked endpoint alone does not establish poor general internet service.
3. On first successful probe, jitter can remain unknown until another valid sample.
   Wi-Fi/mobile/VPN handoff must not reuse the old link's jitter history.
4. Unsupported memory/storage readings remain unknown, not 0% healthy/empty.
   Colours and a 100/100 score are review hints, never malware-free proof.
5. Observe timestamp changes and smooth UI while polling; leave Home/background
   the app and verify the UI-only probe loop does not become a new background job.

## 2. Reachable compatibility self-test and export

- Home → History & tools → Self-test. Check actual model, app version and SDK,
  plus notifications/channel, overlay switch+permission, background configuration,
  Usage/storage access and battery/CPU/RAM/storage availability.
- Toggle one setting manually; returning to the app should refresh the snapshot.
  “Reported/ready” means a value/flag was read, not permission or hardware certification.
- While unplugged, missing watts requests a connected-device test, not a claim that
  the charger sensor is unsupported. Test paused/full charge and unavailable CPU sensors.
- Open the report preview. Confirm it contains only app/model/OS/time and known
  check-ID statuses. Unknown check IDs, raw detail strings, installed-app labels,
  keys, SMS/photos, logs, prompts, network identifiers and serial/device IDs are absent.
- Copy/share is explicit; cancelling preview/share causes no upload. Check a device
  without a matching share activity: report should remain available to read/copy.
- Settings links/overlay rendering/background survival remain **Needs a device test**.
  Repeat the prior cutout/font scale/rotation/permission-denial/plug/unplug/Stop tests.

## 3. Local security timeline

- Recording defaults off. Enabling it must not enable extra background monitoring.
- First successful available categories establish a baseline; existing apps/access
  are not newly granted threats. Initial failed categories get their own baseline
  at first success.
- Install a harmless test app / change an unnecessary test permission / enable a
  test notification/accessibility service manually, then **Run a fresh check**.
  Verify “newly observed”, actual package+component identity and check timestamp.
- Two apps/services with the same display label remain separate targets.
- Mark an observation reviewed/expected: access and warnings remain; it is not
  resolved. Update that test app or change its evidence; a fresh check requires review again.
- Revoke the test access yourself and recheck: “No longer observed” appears only
  with comparable available coverage. It is not a malware-free verdict.
- Deny/interrupt an inventory/AppOps/settings read: retain the last valid baseline;
  no empty result resolves old findings. Restore access and compare to that valid
  baseline, not the failed read. Reduced system-app scope cannot resolve all-app findings.
- App visibility/disappearance is not a proven uninstall; events say first observed/
  no longer listed. Test a profile/package temporarily unavailable to the app.
- Off pauses recording, saved history remains. Clear removes the baseline/events/
  acknowledgements, but never changes permissions or uninstalls apps. Up to 200 events.

## 4. Observed charging sessions and charts

- Logging defaults off. Enable while unplugged, plug in, let it sample and unplug.
  Check start/end percentage, observed duration, power/temperature and session end.
- Enable midway through charging: start time is now and the session is partial;
  no earlier plug time or 20% crossing is invented.
- Test persistent mode off/on, service Stop, force-stop/process kill, reboot while
  connected, paused/full charge and a long gap. Missing ends/reboot interruption
  must be partial; elapsed-clock resets or differing available boot counts must not
  join two boots, even when the new uptime has surpassed the old one. Long gaps
  close a partial segment at the last observed point.
- Adjust wall time during a safe test: durations use elapsed time, not wall time.
- Unknown/invalid sensors produce unknown values, not zero or adapter-rated watts.
  Sampled averages/energy use short valid intervals only, not long missing spans.
- Observe 20→80 continuously if convenient; do not deliberately cycle/deep-drain
  a battery solely for this test. A missing threshold or long gap is **Not fully observed**.
  Compare two observed intervals only with similar conditions, not as an adapter efficiency ranking.
- Graphs retain the latest 240 points; unknown/gap segments stay open. The complete
  observed session's sampled aggregates can cover more than that chart window.
- Up to 30 sessions / 30 days. Clear affects this history only. Enabling logging
  does not promise observation when Android kills/stops the existing monitor.

## 5. Cellular usage and expiry-aware budgeting

- Verify that totals are device-wide cellular/SIM traffic, not Wi-Fi or a particular
  carrier's billing statement. Compare Android Settings and the carrier app without
  treating discrepancies as proof that one counter is broken.
- Configure quota, actual recharge/period-start date and actual expiry separately.
  A past start uses Android-reported totals from local midnight, not a precise carrier
  recharge hour; fallback tracking restarts from observation rather than guessing earlier traffic.
  Test inclusive expiry vs exclusive next-reset-date mode. Future starts are rejected.
- Configure quota separately from actual expiry. Test expiry today (one inclusive
  day), later, invalid date, expired date and removal. Date never auto-resets/recharges.
- Explicit recharge reset begins a new local tracking period; expiry stays unchanged
  until you set it. Forecast pace excludes days before the configured period start.
- With Usage access, view seven reported past full local-calendar days. A successful
  zero remains zero; failure is unknown. Today is partial/in progress, not a completed day.
- Without full statistics, TrafficStats fallback is **partial**. Test reboot/counter
  reset/midnight: preserve observed deltas without claiming missing traffic or assigning
  midnight-spanning deltas to a full new day. Precise quota forecasts are withheld.
- Forecast needs at least three complete past days and positive usable average;
  fewer days/zero/unavailable data does not invent a run-out date. Calendar day
  calculation remains inclusive around DST/time-zone changes.
- Unlimited 5G is user-configured: display/forecast changes, not proof of carrier
  eligibility or zero-rated billing. Measured daily history can remain visible.
- Saving defaults off; viewing past Android statistics is not automatic local history
  sync. Saving is bounded to 90 daily records and clearable without resetting quota/expiry.

## 6. User-controlled AI-linked checklist

- Open locally with no AI configured/offline: deterministic review steps still work.
- Check Do first / Optional / Leave alone–expected. Legitimate banking, accessibility
  and work-admin access must not be blanket-revoked for a score.
- Each app action targets its actual finding/app/component using the guarded routes.
  Health/storage/data/network observations open the relevant existing tool.
- Ask AI: fresh report and optional opaque step IDs appear in the outgoing preview.
  Toggle app-name/network-sharing off and ensure corresponding data is not disclosed.
- Invalid/missing AI checklist JSON or unknown tokens leaves safe local steps; model
  intents, URLs, packages, group/resolved fields do not become executable routes or truth.
- AI explanations attach only to the frozen evidence fingerprint. Recheck after
  app updates/access/temperature changes; stale explanations are not reused.
- Review is an acknowledgement, not completion. Return from settings and explicitly
  recheck if needed; failed reads never report resolved. No automatic revocation,
  uninstall, cache clearing or file deletion is triggered by the checklist.

## Cross-device matrix and automation boundary

Repeat on Redmi 13 5G with exact Android/ROM version, another Xiaomi/Redmi/Poco,
Samsung, Oppo/Realme, Vivo/iQOO and stock/Pixel where available; include Android
8/9/10, Android 11 overlay restrictions and Android 13+ notification permission.
OEM background/autostart policies cannot be established by compilation or a flag snapshot.

CI compiles all five modules/debug/release/AABs; runs both CleanSweep test variants
and existing Radio tests; enforces 113 required CleanSweep cases per variant,
APK feature/manifest checks, version/minSDK and unchanged release-signing checks.
Synthetic parser/test-gate fixtures and these pure policy tests are **not** an
Android UI, emulator, real charging, carrier billing or all-manufacturer test pass.

## Recorded automated result

The [2.35 publication run](https://github.com/universalrp2003/InternetRadio/actions/runs/38069097825) completed successfully on 10 October 2026
for source `51d6d7f7e2e5ed23a931a9551a8368a51dcc2990`: both CleanSweep variants with all 113 required cases
each, all-module builds, release APK/AAB generation and enforced feature/manifest/
version/API-26/signing gates. Tag `v2026.10.10-9` points to that exact source.
The six maintained-app assets are published; previous 2.34 assets remain unchanged.
This adds **no** physical/OEM/carrier validation to the manual checklist above.
