# Live Guard / CleanSweep 2.34 — validation and device test plan

## Scope

Based on the published v2.33 source (tag `v2026.10.10-7`), not the older default
branch. Package remains `com.universalrp.cleansweep`; version name 2.34, code 39.
The stable project signing key is unchanged. Android 8.0/API 26+ remains supported.

## Automated gates

Before publication, Build APK must pass:

- CleanSweep debug **and release** unit tests, including 27 new regression tests:
  - six watts-only/normal-display policy tests;
  - twelve settings routing/identity/API/OEM fallback tests;
  - nine complete AI-prompt, privacy and unavailable-data tests.
- Existing CleanSweep and Ramesh Radio unit tests.
- Debug/release APK and release AAB compilation for all five preserved modules.
- Existing APK/manifest/Dex checks, non-debuggable release checks and signing
  certificate verification. Added checks confirm the new policy/route/prompt classes
  and entry points are actually present in the APK.

Pure JVM policy tests do **not** validate Android overlay rendering or an OEM's
exported Settings activities. No physical phone or emulator was available in this
coding environment. Those limitations must stay in the release notes.

## Charging display (Xiaomi/Redmi/Poco first, then other manufacturers)

1. Update from v2.33 without uninstalling. Confirm app data, AI config, selected pack
   limit/unlimited-5G setting, hide-on-Wi-Fi setting, pill position and scale survive.
2. Enable the status pill, grant overlay permission and enable background monitoring.
   On battery, confirm the normal data count/plan label and D/U lights.
3. Plug in USB, AC and (on a capable device) wireless charging. The floating pill must
   contain **only `n.n W`**: no data count, 5G label, battery %, amps, D/U lights,
   network-grade outline, or pack-quota flashing. Its window must shrink immediately.
   The collapsed notification is charging-only; the expanded card may show battery details.
4. Test no current sensor, full battery, charging paused and a plugged-in phone reporting
   discharging. The display must stay watts-only (`— W` if power is unavailable), not
   revert to a data count or pretend that missing power is zero.
5. Unplug. With background monitoring still enabled, the normal data/meter view must
   return without reopening the app or changing any setting. Data accumulated while
   charging must still be included. Repeat quick plug/unplug cycles to catch stale refreshes.
6. Repeat on Wi-Fi with Hide data enabled, unlimited-5G mode, and 75%/90% quota alerts:
   charging must suppress these temporary visual effects; unplug must restore them.
7. Resize at 70% and 150%, move/drag, rotate, use a cutout/punch-hole phone and open the
   notification shade. Check no stale reserved meter width, clipping or touch interception
   outside explicit positioning mode. An OEM can still limit status-bar overlay placement.
8. With background monitoring OFF, verify charging-only monitoring stops on unplug;
   the fix must not silently opt the user into a persistent background service.
9. Test Stop, permission revocation, process restart, boot/update and MIUI/HyperOS
   battery/autostart restrictions. Android force-stop cannot be bypassed.

## Security Fix / Manage

Use known, deliberately granted test permissions — never install malware for this test.

- For two apps with the same visible label, Manage must still route by the selected
  **package name**, not by a guessed label or the first app in the finding.
- Multi-app Fix/review opens a chooser. Select an app beyond the first six; verify
  the selected package/component reaches the helper.
- Check all app findings: SMS/banking SMS, unknown-app installation, usage access,
  overlay, sideloaded/unusual-installer apps, preinstalled stubs and root-related tools.
- Check notification service details on API 30+, plus the category fallback on API 26–29.
- Accessibility detail is privileged: the public category/highlight plus named manual
  instructions are expected, not a false promise of a direct service toggle.
- Overlay's public package URI is ignored on API 30+: correct category plus the named
  app/manual instructions are expected. API 26–29 should use a per-app URI.
- Check device-admin list (without an ADD_DEVICE_ADMIN request), screen-lock setup,
  Developer options and system update/About phone. Never fall back to the Settings homepage.
- On MIUI/HyperOS, test SMS permission-editor candidates and fallback to selected App info.
  Missing/non-exported OEM activities must not crash; show the manual path.
- Revoke a permission, return to Live Guard, and confirm the fresh scan removes that
  finding. Essential banking/accessibility/work-admin permissions are not auto-revoked.

## Ask AI about all current issues

- Configure an AI provider (or explicitly select Free), then use the security button.
  It must open the security AI report, not ask the offline assistant a generic sentence.
- Change/revoke a permission immediately before the tap; inspect “See exactly what was
  sent” to confirm a **new** app inventory/security scan and battery/storage readings.
- Ensure every current finding and every affected app is included, not just UI's first
  six samples or inventory's first sixty. Confirm severity, count, evidence, manual
  hint, exact package/service IDs when shared, and phone/Android version.
- Existing malware/junk/LAN results must carry dates and be marked as older results;
  no new malware lookup, junk scan or LAN sweep may start just to ask the AI.
- Turn app-name sharing OFF: labels, packages and service components must not appear,
  including embedded descriptions and malware-hit labels. Counts/findings remain.
- Turn network sharing OFF: no Wi-Fi identifiers/IP/DNS, data usage or live network
  observations. No web-search request is made with phone findings.
- Verify missing sensors, denied local reads, unrun checks and a still-running malware
  scan are labelled unknown/incomplete, never zero, safe or malware-free.
- Provider failures, fallback disclosure, cancellation and repeat analysis must leave
  no endless spinner. “Analyse again” preserves security-focused mode and answer language.
- Read the AI answer critically: it should address all findings without demanding that
  useful permissions be removed just to reach a heuristic 100/100 score.
