# OorArivippu — test build

This is the first integration of the user-provided OorArivippu Android source into the RP Ideas Gradle project. The current scope is a **debug/test APK only** (`0.1.0-test`); the OorArivippu module is not included in any production release task.

## Build

```bash
./gradlew :oorarivippu:testDebugUnitTest :oorarivippu:lintDebug :oorarivippu:assembleDebug
```

The dedicated GitHub Actions workflow uploads `oorarivippu/build/outputs/apk/debug/*.apk` as a short-lived test artifact. Debug APKs are signed with CI's debug key, so a later CI build may require uninstalling the previous test build before installation.

## First device-test checklist

- Install on Android 8.0/API 26 or newer. The configured application ID is `com.oorarivippu.voiceannouncer`.
- Use **Test Voice** in both Tamil and English. The UI reports whether the selected voice is available, whether it fell back to the other installed language, or whether neither voice is available. If needed, install voice data in the device's Text-to-Speech settings and retest.
- To track a trip, grant foreground location and (on Android 13+) notification permissions, then start tracking while the app is visible. Precise location is recommended; approximate location is accepted but may identify places less accurately.
- On Android 10+, the app offers a separate, optional background-location step. For the full screen-off test, grant **Allow all the time** (Android 11+ may send you to App info/Location settings). You can continue without it, but recovery and background behavior can vary by Android version and OEM. The app does not request automatic boot-start permission.
- Confirm the ongoing notification appears and its stop action stops tracking. Also test the in-app OFF button. On OPPO and Xiaomi, note any battery/background restrictions that stop or delay the service; do not disable OEM safeguards globally just for the test.
- Test outdoors with GPS enabled and network access available for place-name lookup. Turn GPS off and back on while tracking; announcements should pause and resume. Android's geocoder may be unavailable, offline, delayed, or return incomplete/localized names on a particular device.
- Try both immediate-on-start settings and the movement-distance control. Location fixes are requested about every five seconds; reverse geocoding is rate-limited to at most once per 30 seconds and generally requires at least 100 m displacement. This is a battery-conscious first pass, not a guarantee of boundary-level or real-time place detection.
- Keep in mind that continuous GPS tracking can increase battery use. Stop tracking when finished.

The current CI job runs unit tests, Android lint, and a debug build. It does **not** emulate GPS/TTS or replace an on-device trip test. No production release or public APK release is part of this test pass.
