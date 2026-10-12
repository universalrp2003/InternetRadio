# Live Guard 2.41 (code 46) — device test plan

Purpose: prove on the real phone that the 2.41 candidate **installs** (with Play Protect
left ON), that the four owner-requested changes behave as described, and that nothing else
regressed. Nothing in this plan can be claimed from CI results — a green CI build is not an
install, a Play Protect verdict or a device test.

Affected phone context: **Redmi 13 5G**, earlier screenshots indicated **Android 16 / API 36**.
The exact ROM is unknown — confirm it (Settings → About phone → OS version / build number)
and do not write "HyperOS" or "MIUI" unless the phone actually says so.

## 0. Before installing

- [ ] Candidate APK SHA-256 taken from the run summary, the `.apk.sha256` sidecar inside the
      `cleansweep-apk` artifact, or `docs/candidate-build-digests.md` on the candidate branch,
      and re-checked on the downloaded file.
- [ ] Install route: the **normal download route** (GitHub artifact/release link), Play
      Protect **left ON**. No adb, no `pm install` bypass, no modified build.
- [ ] Current Live Guard version noted (expected: 2.40/code45 or older), because the upgrade
      path is part of the test.

## 1. Installation (the decisive step)

- [ ] Attempt the install and record the **exact** on-screen text, word for word.
- [ ] If Play Protect blocks: screenshot it, note the exact hash/version attempted and
      **stop** — do not retry with a workaround.
- [ ] If it installs: confirm the installed version shows **2.41** (Android Settings → Apps →
      Live Guard) and that it updated over the old install without losing settings.
- [ ] Record the outcome in `docs/live-guard-2.41-device-evidence.md` together with the exact
      APK SHA-256 that was installed and the CI run that produced it, as the machine-readable
      `TESTED-APK-SHA256:` and `TESTED-RUN-ID:` lines. Publication stays blocked without them.

## 2. Honest watts/amps (new in 2.41)

- [ ] Plug in the charger and open the ongoing card plus the battery screen.
- [ ] The charging figure must say it is measured **at the battery**; no line may present a
      current value as if it were a wattage (or vice versa).
- [ ] If the phone does not report a current or voltage, the app must say it is not reported —
      never `0.00 W` and never a made-up number.
- [ ] Compare with what the phone's own battery screen shows: the app must not claim to know
      the charger adapter's output.

## 3. Screen-on/off charging note (new in 2.41)

- [ ] While charging with the screen **on**, the note must appear, saying that the phone's own
      draw can make the battery-side value read lower than with the screen off.
- [ ] Turn the screen **off**, wait a few seconds, and re-open: the note must change to the
      screen-off wording (or is absent when unplugged).
- [ ] The note must read as a measurement note — no claim that anything is wrong.

## 4. 44 °C heat warning (new in 2.41)

- [ ] With the battery at or above 44 °C (charging while gaming is the easy way to reach it),
      the warning must appear/spoken once with the real temperature.
- [ ] A second warning must not repeat inside three hours.
- [ ] Below 44 °C the warning must **not** appear, and with no temperature sensor the app must
      not invent one.

## 5. AI output budget + provider model check (new in 2.41)

- [ ] AI settings → **Load models**: with the saved model present in the provider's list, the
      list loads normally; with a model the provider no longer lists, the app must say the
      selected model could not be used instead of sending a doomed request.
- [ ] A normal question and a phone report must still answer, inside the usual time limits.
- [ ] If the provider is unreachable, the error must stay the plain translatable message —
      no provider HTML, URL or key material.

## 6. Everything from 2.40 must still hold

- [ ] Notification reading is still gone (no "Selected notification reading" menu, no listener
      entry in Android's notification access list).
- [ ] Cleanup, security, storage, data plan, charger monitor, widgets, voice and the updater
      behave as before; settings survive the upgrade.
- [ ] About still shows the version, package and stable signing identity you expect.

## 7. Reporting back

Give the exact Play Protect sentence and installer outcome, the version shown in the app, the
phone model/Android version, and anything that behaved differently. Do not describe results
from another phone or an older build as this test.
