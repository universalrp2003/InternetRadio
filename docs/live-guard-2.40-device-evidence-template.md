# Live Guard 2.40 device evidence — TEMPLATE (copy, do not publish this file)

Copy this file to **`docs/live-guard-2.40-device-evidence.md`** and replace every
placeholder with real observations. The CI publication step for a CleanSweep version newer
than 2.39 refuses to publish unless that file exists, mentions the **actual Play Protect
result** and the **installer outcome**, and contains the **exact SHA-256** of the APK that
is being published. This template deliberately keeps the gate closed.

- Candidate: Live Guard / CleanSweep 2.40, versionCode 45
- Exact APK SHA-256: `<sha256 of the exact APK that was installed>`
- APK file name: `CleanSweep-v2.40.apk`
- CI run / job: `<run id> / <job id>`
- Compiled source commit: `<40-hex commit the APK was built from>`
- Download route used: `<normal public download route (release or CI artifact link)>`
- Phone: Redmi 13 5G — ROM/build: `<exact string from Settings, e.g. "Android 16, build …">`
- Play Protect state during install: **ON** (do not disable it for the test)
- Date/time of the test (phone-local): `<yyyy-mm-dd hh:mm AM/PM>`
- Previous installed version: `<e.g. 2.39 / code44>`

## Play Protect result (required wording)

`<Quote the exact on-screen Play Protect text. Example: "App blocked to protect your
device" / no warning shown / "This app can request access to sensitive data…".>`

## Installer outcome (required wording)

`<Did Android's installer complete? Did the app open? Error text? Did it update over the
previous version without losing settings?>`

## Retired-capability checks

- Notification access list: `<Live Guard absent / still listed (record it)>`
- Reader menu/row in the app: `<absent / present (record it)>`
- Leftover `liveguard_notification_reader` file: `<gone / still present (record it)>`

## Kept features (brief, factual)

`<Battery card, cleanup, cache screen, charging card, one audible alert, widgets, pill,
assistant, updater, EN/TA. Only record what was actually observed.>`

## Result

`<INSTALLED AND OPENED / BLOCKED / OTHER — with the exact wording.>`

## Limits of this evidence

This records one phone, one ROM and one moment. It is not Google's approval, not Play
Protect clearance for other devices or future builds, and it does not certify OEM
background behaviour, audibility or every listed feature.
