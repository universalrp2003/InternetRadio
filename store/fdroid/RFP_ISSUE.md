# Paste-ready text: F-Droid "Requests For Packaging" issue

Open <https://gitlab.com/fdroid/rfp/-/issues/new> and paste the block below. One issue can cover
all five apps because they live in the same repository.

---

**Title:** Five apps by one author in one repository (CleanSweep, Ramesh Radio, Internet Radio, AppForge, PulseEQ)

**Body:**

Hello, and thank you for F-Droid. I would like to request packaging of five apps that share one
Gradle project. Each one is a separate application id and a separate module; the recipes below
use `subdir` to select the module. I have already written the metadata files — they are the same
files that would go into `metadata/` in this tracker's repository, and they live in my
repository at `store/fdroid/metadata/`.

* **Repository:** https://github.com/universalrp2003/InternetRadio
* **Licence:** GPL-3.0-or-later (see `LICENSE` in the repository root)
* **Author:** Ramesh prathap .R <universalrp2003@gmail.com>
* **Release tag for these versions:** `v2026.10.06` (GitHub Release with the APKs:
  https://github.com/universalrp2003/InternetRadio/releases)
* **No proprietary dependencies:** no Google Play Services, no Firebase, no ads, no analytics,
  no crash reporting. Verified by CI on every build.
* **Descriptions, screenshots folder and Tamil translation** are in each module under
  `<module>/fastlane/metadata/android/` (English `en-US`, Tamil `ta-IN` for CleanSweep and
  Ramesh Radio).

## The five apps

| App | Application ID | Module (`subdir`) | Licence | Version |
|---|---|---|---|---|
| **CleanSweep** — junk cleaner, battery/health/security/network tools, optional user-key AI | `com.universalrp.cleansweep` | `cleaner` | GPL-3.0-or-later | 2.6 (11) |
| **Ramesh Radio** — Tamil FM, Tamil and world news radio, local player, 10-band EQ | `com.universalrp.tamilnadufm` | `radio` | GPL-3.0-or-later | 1.2 (2) |
| **Internet Radio** — streaming radio with background playback | `com.universalrp.internetradio` | `app` | GPL-3.0-or-later | 1.0 (2) |
| **AppForge** — build a small app on the phone, export HTML or an Android project | `com.universalrp.appforge` | `builder` | GPL-3.0-or-later | 1.0 (1) |
| **PulseEQ** — 20-band equalizer with its own DSP (declares **no** INTERNET permission) | `com.universalrp.pulseeq` | `equalizer` | GPL-3.0-or-later | 1.0 (1) |

## Build notes

* Gradle project with a committed wrapper (Gradle 8.14.3, JDK 17, AGP as in
  `gradle/libs.versions.toml` / module build files).
* Recipe: `subdir: <module>`, `gradle: [yes]` (release build), `UpdateCheckMode: RepoManifest`
  because the five apps version independently in one repository.
* CleanSweep uses `MANAGE_EXTERNAL_STORAGE` (All files access). It is a cleaner: the permission
  is the feature, nothing is deleted without the user selecting it and confirming, and the
  listing text says so plainly.
* Ramesh Radio and Internet Radio use INTERNET to stream the station the user chose. PulseEQ
  has no INTERNET permission at all.
* CleanSweep's optional AI sends text to the provider whose API key the user pasted, only when
  the user taps Analyse/Ask; the app shows exactly what was sent. No key ships with the app.
* CleanSweep has no Accessibility service and never taps inside other apps.

I am happy to answer questions or change the recipes to whatever you prefer.
