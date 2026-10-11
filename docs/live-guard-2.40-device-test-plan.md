# Live Guard 2.40 (core / no notification reading) — device test plan

Purpose: prove on a real phone that the candidate **installs**, that Play Protect's
behaviour is recorded honestly, and that removing the reader did not break the features
that stay. Nothing in this plan may be claimed from CI results.

Affected phone context: **Redmi 13 5G**, earlier screenshots indicated **Android 16 / API 36**.
The exact ROM is unknown — confirm it (Settings → About phone → OS version / build number)
and do not write "HyperOS" or "MIUI" unless the phone actually says so.

## 0. Before installing

- [ ] Candidate APK SHA-256 taken from the run summary, the `.apk.sha256` sidecar inside the
      `cleansweep-apk` artifact, or `docs/candidate-build-digests.md` on the candidate branch,
      and re-checked on the downloaded file.
- [ ] Install route: the **normal download route** (GitHub release/artifact link), Play
      Protect **left ON**. Do not use adb, `pm install` bypasses or a modified build.
- [ ] Previous Live Guard version noted (expected: 2.39/code44 or older), because the
      upgrade path is part of the test.

## 1. Installation (the decisive step)

- [ ] Attempt the install and record the **exact** on-screen text.
- [ ] If Play Protect blocks: screenshot it, note the exact APK hash/version attempted, and
      **stop** — do not retry with a workaround.
- [ ] If it installs: confirm the installed version shows 2.40 (Android Settings → Apps →
      Live Guard) and that it updated over the old install without losing settings.

## 2. Retired capability must not exist

- [ ] Android Settings → Notifications → Device & app notifications (or Special app
      access → Notification access): Live Guard must **not** appear as a listener. If a
      leftover entry exists from 2.39, that is a real finding — record it.
- [ ] No "Selected notification reading" menu item anywhere in the app.
- [ ] Feature setup list contains no notification-reader row.

## 3. Cleanup after upgrading from a reader-using 2.39

- [ ] Open the app once (cleanup runs at startup), then check the app's private storage is
      consistent with the cleanup: the `liveguard_notification_reader` file is gone.
- [ ] Confirm nothing user-chosen was reset: API key still saved, app language unchanged,
      Voice A/B selections unchanged, voice/monitor switches unchanged (**including saved
      OFF choices**), data plan, charging history, security history and the last
      permission-review record all still present.

## 4. Kept features — smoke test

- [ ] Battery/phone health opens with real readings; the estimated phone age badge is
      visible and still labelled as an estimate.
- [ ] Cleanup: scan, review, select, clean — and the reported freed size matches reality.
- [ ] App cache screen refreshes when opened; system apps included by default; **nothing
      auto-selected or auto-deleted**.
- [ ] Charging card appears while charging (watts, %, time to full) and stops on unplug.
- [ ] One ordinary alert actually arrives and is audible (e.g. battery low). Manual "read
      aloud" working is **not** evidence that automatic alerts are audible — test them
      separately.
- [ ] Widgets: Battery / Data / Phone add, refresh and show genuine observed unplug
      time/level (and stay "unknown" instead of inventing a stamp).
- [ ] Floating pill shows charging watts and can be turned off.
- [ ] Assistant: one chat answer within the 45s budget with progress/cancel, and one
      report/scan. Online and offline states both behave.
- [ ] In-app updater: check for published updates; the Android permission/installer
      confirmation path must appear as expected (no silent install).
- [ ] Language: switch EN ⇄ TA and confirm no untranslated leftover blocks remain on the
      screens that used to mention notification reading.

## 5. Recording the result

Copy `docs/live-guard-2.40-device-evidence-template.md` to
`docs/live-guard-2.40-device-evidence.md`, fill in the real observations (including the
`TESTED-APK-SHA256` and `TESTED-RUN-ID` lines) and commit it. That file is what the CI
publication step requires for a CleanSweep version newer than 2.39, and it is also what
makes the workflow publish those exact bytes rather than a rebuild.

If the result is a block: this plan ends at step 1. Do the policy/privacy review and use
Google's official channels — never disable Play Protect, never disguise the source, never
change the package or signing identity.
