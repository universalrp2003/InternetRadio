# Oor Arivippu — Tamil/English place announcer

**Current stable release:** 1.0.0 / code 2

**Requested stable release:** 1.0.1 / code 3 (`Oor-Arivippu-v1.0.1.apk`, tag `oor-arivippu-v1.0.1`). The owner tested the debug APK from [run 38776902130](https://github.com/universalrp2003/InternetRadio/actions/runs/38776902130) and authorized publication; the Oor test workflow then passed on the exact 1.0.1 source in [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762). 1.0.0 remains the current stable release until both repositories serve the verified 1.0.1 APK. See the [1.0.1 release notes](../releases/oor-arivippu-v1.0.1.md).

**Package:** `com.oorarivippu.voiceannouncer`

**Minimum Android:** 8.0 / API 26

Oor Arivippu is a GPS place and destination announcer. It is a fifth active RP Ideas product, published as a standalone release outside the existing four-app release batch.

## Download the stable release

- [RP-Android-Apps release](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.0.0) — primary updater source.
- [InternetRadio release](https://github.com/universalrp2003/InternetRadio/releases/tag/oor-arivippu-v1.0.0) — verified fallback.
- [Release notes](../releases/oor-arivippu-v1.0.0.md) · [published APK size and SHA-256 ledger](../docs/published-release-digests.md).

Both public releases carry `Oor-Arivippu-v1.0.0.apk`, 12,179,013 bytes, SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`. GitHub Actions [run 38748688974](https://github.com/universalrp2003/InternetRadio/actions/runs/38748688974) passed the release unit tests, lint, signed build and package/version/minimum-SDK/signature checks; it verified the two public assets have identical digest and size and canonical URLs.

The owner reports that the stable app is working and has been tested on two devices. Supplied screenshots show the v1.0.0 (code 2) package, Play Protect's “This app looks safe” screen, and the running app's About/update screen reporting v1.0.0 with no newer release. This supports owner-reported device validation of the release version, but the screenshots do not show the APK SHA-256 or prove the installed bytes match this exact public asset.

Earlier test APKs were debug-signed. Android may not allow a debug-signed test installation to update to the stable RP Ideas signer. If Android rejects the update, uninstall the test APK before installing stable; uninstalling removes that test installation's local settings and announcement history. Android still controls its per-app **Install unknown apps** permission, confirmation screen and Play Protect decision. The app does not install silently or bypass those checks.

## Connectivity and offline reminders

When internet access is unavailable, GPS tracking may continue, but place-name, district, highway/landmark and destination speech announcements are paused. The interface and ongoing notification state that the internet is unavailable. If enabled, an offline-status reminder is sent immediately when tracking starts offline or connectivity is lost, then at the selected interval: **Off, 5, 10, 15 or 30 minutes** (15 minutes by default). It uses an installed on-device TTS voice when available, otherwise a short local beep; it does not use online voice services. Destination lookup and place names depend on Android's Geocoder and may also fail when its service is unavailable.

## Device-test checklist

- Choose up to four voices offered by the Android TTS engine for Tamil or English. Voice labels identify whether Android marks each as on-device or Internet-dependent; the system default remains available. Network voices may send spoken text to their TTS provider. Offline reminders still force an on-device voice or a beep.
- Use **Test Voice** in both Tamil and English. The UI reports whether the selected voice is available, whether it fell back to the other language, or whether the selected voice is no longer available. If needed, install voice data in the device's Text-to-Speech settings and reopen the app to refresh the list.
- To track a trip, grant foreground location and (on Android 13+) notification permissions, then start tracking while the app is visible. Precise location is recommended; approximate location is accepted but may identify places less accurately.
- On Android 10+, the app offers a separate, optional background-location step. For the full screen-off test, grant **Allow all the time** (Android 11+ may send you to App info/Location settings). You can continue without it, but recovery and background behavior can vary by Android version and OEM. The app does not request automatic boot-start permission.
- Confirm the ongoing notification appears and its stop action stops tracking. Also test the in-app OFF button. On OPPO and Xiaomi, note any battery/background restrictions that stop or delay the service; do not disable OEM safeguards globally just for the test.
- Confirm Plus Codes such as `9X5H+3HF`, coordinate-only labels, and numeric address fragments such as `1/242` are not announced as place names. A Plus Code changing within Alangudi should stay silent; a real named change such as **Alangudi Hospital → Alangudi Bus Stand** should still announce when same-city announcements are enabled. Long multi-stop road descriptions should not replace a named place, and the speed/motion label should appear on separate lines.
- Test connectivity deliberately while tracking: switch off Wi-Fi and mobile data. Confirm the UI/notification clearly says there is **no internet**, place/district/highway/destination speech stops, and the selected offline reminder still uses local TTS or a beep. GPS status can continue updating. Reconnect and check that place lookup resumes. Android's network-validation signal and geocoder service can vary, so report any false status or lookup failure.
- Set a destination while online using a town/city name. Confirm the 10 km, 5 km, 2 km and approximately 500 m arrival alerts, then clear or change it. The Android Geocoder's first match is used; verify the selected place before relying on the alert. Destination speech remains paused without internet; the separate offline-status reminder may continue.
- Test a phone or VoIP call while tracking. The app uses Android's audio mode as a privacy-minimal, best-effort call signal; it should pause new speech and speak the latest queued place after the mode returns to normal. Some call apps/OEMs may not expose a detectable mode.
- The optional headset **Next/skip** media-button trigger is off by default. Enable it in settings, start tracking, and test the headset button. Media-button routing varies by headset/OEM and may conflict with music controls; disable it if it does.
- District alerts use the Geocoder's `subAdminArea`; highway/toll/junction announcements occur only when returned road/feature names match recognized highway or landmark terms. Neither is a complete offline map database, and some locations will not provide these fields.
- Observe the displayed adaptive GPS interval: about 45 seconds while stationary, 15 seconds in ordinary traffic, and 5 seconds at highway speeds. These are requested intervals, not a measured battery-saving percentage; Android/OEM batching, GPS quality and movement detection can change real behavior. Reverse geocoding is limited to at most once per 30 seconds and generally requires at least 100 m displacement.
- Continuous GPS tracking can still use substantial battery. Stop tracking when finished.

## Build and CI

```bash
./gradlew :oorarivippu:testDebugUnitTest :oorarivippu:lintDebug :oorarivippu:assembleDebug
./gradlew :oorarivippu:testReleaseUnitTest :oorarivippu:lintRelease :oorarivippu:assembleRelease
```

The dedicated [test-APK workflow](../.github/workflows/oorarivippu-test-apk.yml) uploads a short-lived debug artifact; it is for testing, not the stable updater. Stable releases are built and checked separately by the [standalone release workflow](../.github/workflows/oorarivippu-release.yml), which publishes only after an explicit authorized release trigger.

CI runs unit tests, Android lint and APK checks. It does **not** emulate GPS, phone calls, Bluetooth media buttons, TTS, network loss or long-trip battery use, and it does not replace on-device validation. See [`../PRIVACY.md`](../PRIVACY.md) for location and Geocoder data handling.
