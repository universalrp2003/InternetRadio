# Live Guard 2.40 device evidence

Filled from `docs/live-guard-2.40-device-evidence-template.md`. The CI publication step
for a CleanSweep version newer than 2.39 refuses to publish unless this file exists,
mentions the **actual Play Protect result** and the **installer outcome**, and carries
the machine-readable lines below. The `TESTED-APK-SHA256` / `TESTED-RUN-ID` lines are
what the workflow uses to publish **the same bytes that were installed**: if the rebuild
reproduces that digest the freshly built APK is published; otherwise the workflow
downloads the recorded run's `cleansweep-apk` artifact, verifies its digest against these
lines and publishes those bytes instead of rebuilding.

- Candidate: Live Guard / CleanSweep 2.40, versionCode 45
- APK file name: `CleanSweep-v2.40.apk`
- Compiled source commit: `92ffd31b4cf8a4141769ca5dd1d141354807eefa`
- Download route used: the normal public artifact route — the `cleansweep-apk` artifact
  of CI run 38149080790 (that run's digest table is in its run summary and in the
  `docs/candidate-build-digests.md` record preserved at commit `cfeec088fc23`; each later
  CI run refreshes that file with its own build digests)

TESTED-APK-SHA256: 9a802bffc6e4ff17bc572be3c19f612f92a23c133790042f87234da20fe20869
TESTED-RUN-ID: 38149080790
TESTED-APK-SIZE-BYTES: 13558548
TESTED-JOB-ID: 114498516971
- Phone: Redmi 13 5G — ROM/build: Xiaomi HyperOS 3.0.303.0, Android 16, build BP2A.250605.031.A3
- Play Protect state during install: **ON** (it was not disabled for the test)
- Date/time of the test (phone-local): 2026-10-11 (exact clock time not recorded)
- Previous installed version: not recorded. The published 2.39 APK had been **blocked**
  by Play Protect on this same phone, so 2.39 was never installed on it.

## Play Protect result (required wording)

Play Protect showed: **"This app looks safe - You can continue to install it"**.

## Installer outcome (required wording)

Android's installer completed. The app opened, and About shows **CleanSweep v2.40**.

## Retired-capability checks

- Notification access list: not recorded in this test session
- Reader menu/row in the app: not recorded in this test session
- Leftover `liveguard_notification_reader` file: not recorded in this test session

(These smoke checks from `docs/live-guard-2.40-device-test-plan.md` were not recorded
with this installation result and are not claimed here.)

## Kept features (brief, factual)

Not separately recorded in this session. Only the installation outcome above and the
About screen showing CleanSweep v2.40 were observed.

## Result

**INSTALLED AND OPENED.** Play Protect (ON) said "This app looks safe - You can continue
to install it", Android's installer completed, the app opened, and About shows CleanSweep
v2.40. The same phone had blocked the published 2.39 APK ("App blocked to protect your
device").

## Limits of this evidence

This records one phone, one ROM and one moment — one phone, one build. It is **not**
Google's approval, **not** Play Protect clearance for other devices or future builds,
**not** all-device certification, and **not** proof that removing the notification reader
caused the different result (the 2.39 block's cause was never confirmed). It does not
certify OEM background behaviour, audibility or every listed feature.
