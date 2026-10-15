# OorArivippu — test build

This is the first integration of the user-provided OorArivippu Android source into the RP Ideas Gradle project. The current scope is a **debug/test APK only** (`0.1.0-test`); the OorArivippu module is not included in any production release task.

## Build

```bash
./gradlew :oorarivippu:testDebugUnitTest :oorarivippu:lintDebug :oorarivippu:assembleDebug
```

The dedicated GitHub Actions workflow uploads `oorarivippu/build/outputs/apk/debug/*.apk` as a short-lived test artifact. Debug APKs are signed with CI's debug key, so a later CI build may require uninstalling the previous test build before installation.

## Device-test checklist

- Install on Android 8.0/API 26 or newer. The configured application ID is `com.oorarivippu.voiceannouncer`.
- Use **Test Voice** in both Tamil and English. The UI reports whether the selected voice is available, whether it fell back to the other installed language, or whether neither voice is available. If needed, install voice data in the device's Text-to-Speech settings and retest.
- To track a trip, grant foreground location and (on Android 13+) notification permissions, then start tracking while the app is visible. Precise location is recommended; approximate location is accepted but may identify places less accurately.
- On Android 10+, the app offers a separate, optional background-location step. For the full screen-off test, grant **Allow all the time** (Android 11+ may send you to App info/Location settings). You can continue without it, but recovery and background behavior can vary by Android version and OEM. The app does not request automatic boot-start permission.
- Confirm the ongoing notification appears and its stop action stops tracking. Also test the in-app OFF button. On OPPO and Xiaomi, note any battery/background restrictions that stop or delay the service; do not disable OEM safeguards globally just for the test.
- Test the screenshot fixes: with **Announce Current Place When Turned ON** enabled, starting while stationary should announce and log the current town; a numeric sub-area such as `1/242` should be filtered; and the speed/motion label should be on separate lines rather than crowded together.
- Test connectivity deliberately: while tracking, switch off Wi-Fi and mobile data. The UI/ongoing notification should report that validated internet is unavailable and place-name, district, highway, and destination voice announcements are paused. GPS status can continue updating. The local **Test Voice** button still tests installed TTS without internet. Reconnect and check that place lookup resumes. Android's network-validation signal and geocoder service can vary, so report any false status or lookup failure.
- Set a destination while online using a town/city name. Confirm the 10 km, 5 km, 2 km and approximately 500 m arrival alerts, then clear or change it. The Android geocoder's first match is used; verify the selected place is correct before relying on the alarm. Internet-offline voice alerts are intentionally suppressed.
- Test a phone or VoIP call while tracking. The app uses Android's audio mode as a privacy-minimal, best-effort call signal; it should pause new speech and speak the latest queued place after the mode returns to normal. Some call apps/OEMs may not expose a detectable mode.
- The optional headset **Next/skip** media-button trigger is off by default. Enable it in settings, start tracking, and test the headset button. Media-button routing varies by headset/OEM and may conflict with music controls; disable it if it does.
- District alerts use the geocoder's `subAdminArea`; highway/toll/junction announcements occur only when returned road/feature names match recognized highway or landmark terms. Neither is a complete offline map database, and some locations will not provide these fields.
- Observe the displayed adaptive GPS interval: about 45 seconds while stationary, 15 seconds in ordinary traffic, and 5 seconds at highway speeds. These are requested intervals, not a measured battery-saving percentage; Android/OEM batching, GPS quality, and movement detection can change real behavior. Reverse geocoding remains gated to at most once per 30 seconds and generally requires at least 100 m displacement.
- Continuous GPS tracking can still use substantial battery. Stop tracking when finished.

The CI job runs unit tests, Android lint, and a debug build. It does **not** emulate GPS, phone calls, Bluetooth media buttons, TTS, network loss, or long-trip battery use, and it does not replace the OPPO/Xiaomi on-device checks. No production release or public APK release is part of this test pass.
