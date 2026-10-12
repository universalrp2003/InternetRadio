# Live Guard 2.41 device evidence

Filled from the owner's real install of the candidate. The CI publication step for a
CleanSweep version newer than 2.39 refuses to publish unless this file exists, mentions the
**actual Play Protect result** and the **installer outcome**, and carries the
machine-readable lines below. The `TESTED-APK-SHA256` / `TESTED-RUN-ID` lines are what the
workflow uses to publish **the same bytes that were installed**: if a rebuild reproduces
that digest the freshly built APK is published; otherwise the workflow downloads the
recorded run's `cleansweep-apk` artifact, verifies its digest against these lines and
publishes those bytes instead of a rebuild.

- Candidate: Live Guard / CleanSweep 2.41, versionCode 46
- APK file name: `CleanSweep-v2.41.apk` (release variant, 13,565,004 bytes recorded by CI)
- Compiled source commit: `e31828f74bbe25f6c8e59c278284c06fe24826f3`
- Download route used: the normal public artifact route — the `cleansweep-apk` artifact of
  CI run **38202739572**, downloaded on the phone as `cleansweep-apk.zip` and extracted to
  `/storage/emulated/0/Download/cleansweep-apk/`. The extracted `release/` folder held
  `CleanSweep-v2.41.apk` (13.57 MB as shown by the phone) together with its
  `CleanSweep-v2.41.apk.sha256` sidecar.
- Phone: Redmi 13 5G — the same phone as
  [`docs/live-guard-2.40-device-evidence.md`](live-guard-2.40-device-evidence.md) (Xiaomi
  HyperOS 3.0.303.0, Android 16, build BP2A.250605.031.A3). The ROM was **not re-checked** in
  this session; the screenshots show Android 16 and the app's own About screen.
- Play Protect state during install: **ON** (it was not disabled for the test)
- Date/time of the test (phone-local): 2026-10-12, installer screens at ~09:50–09:51
- Previous installed version: **2.40 (45)** — shown by the app-info dialog next to the new
  APK's `2.41 (46)`, so this was an in-place upgrade, not a fresh install.

TESTED-APK-SHA256: f728a3435f12952370759feed743431c01179b95e84ba48259bae89a35ba7a03
TESTED-RUN-ID: 38202739572
TESTED-APK-SIZE-BYTES: 13565004

## Play Protect result (required wording)

Play Protect (ON) showed, in order, exactly:

1. **"App scan recommended"** — *"Play Protect hasn't seen this app before. To protect your
   device and data, send this app to Google for a security scan."* with a "More details"
   expander and the buttons **"Scan app"** / **"Don't install app"**.
2. **"Scanning…"** — *"Scanning the app may take a little while. You'll see results on this
   screen."* with the button **"Cancel and don't install"**.
3. **"This app looks safe"** — *"You can continue to install it"* with the buttons
   **"Install"** / **"Don't install"**.

Wording transcribed from the owner's screenshots of the Play Protect screens
(`Screenshot_2026-10-12-09-51-13-179_com.android.vending.jpg`,
`…-09-51-23-062_com.android.vending.jpg`, `…-09-51-42-825_com.android.vending.jpg`).
The scan was the normal Google scan offered by the installer; Play Protect was not disabled,
bypassed or worked around.

## Installer outcome (required wording)

Android's installer ran normally: the progress dialog showed **"Installing…"**
(`Screenshot_2026-10-12-09-50-48-810_com.google.android.packageinstaller.jpg`), completed, and
the app opened. The app's own **About Live Guard** screen shows **"CleanSweep v2.41"**, and
Android's app info shows the installed version as **2.41 (46)** under package
`com.universalrp.cleansweep`.

## The owner's result, in the owner's own words

> **"all other are working good its installed without problems"**

The owner also reported, while testing the assistant:

> **"if i type and ask questions its appeard"**

— describing the existing in-app prompt *"Choose and preview two voices for this answer
language in Voice & daily watch."* that appears when "Read AI answers aloud" is on but no A/B
voice has been chosen for the answer language. It is recorded here as observed behaviour, not
as a failure: the prompt is the app telling the owner which settings to pick, and it is not a
crash, block or data problem.

## What was actually observed

- Installation and first launch (above), including the in-place upgrade from 2.40 (45).
- **About** shows CleanSweep v2.41; the app runs.
- **Assistant**: the owner asked "hi how are you" and received an answer ("I'm doing well,
  thanks for asking! How can I help you today?") from **Online AI · NVIDIA NIM**
  (`nvidia/nemotron-3-n…` shown in the header), i.e. the configured provider path works.
- **Voice & daily watch** screen opens and shows the Tamil/English tabs, Voice A/B selection
  and the existing explanations.

## Not separately recorded in this session

- No charging screenshot was taken, so the 2.41 honest battery-side watts/amps labels and the
  screen-on/off note were **not individually verified** in this record; the owner's statement
  covers the app working normally.
- The 44 °C heat warning was not triggered/observed in this session.
- The provider model-list check message after "Load models" was not separately exercised.
- Notification-access list, cleanup, security scan, storage, widgets and the in-app updater
  were not re-checked against this exact build in this session.

## Result

**INSTALLED AND OPENED.** Play Protect (ON) showed "App scan recommended" → "Scanning…" →
"This app looks safe / You can continue to install it", Android's installer completed, and
About shows CleanSweep **v2.41**. The same phone had blocked the published 2.39 APK
("App blocked to protect your device") and installed 2.40 with "This app looks safe".

## Limits of this evidence

This records one phone, one ROM and one moment — **one phone, one build**. It is **not**
Google's approval, **not** Play Protect clearance for other devices or future builds, **not**
all-device certification, and **not** proof of any cause. It does not certify OEM background
behaviour, audibility, every listed feature or the 2.41-specific readings that were not
separately observed above.
