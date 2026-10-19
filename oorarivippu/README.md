# Oor Arivippu — Tamil/English place announcer

**Current stable release:** 1.1.0 / code 5

**Package:** `com.oorarivippu.voiceannouncer`

**Minimum Android:** 8.0 / API 26

Oor Arivippu is a GPS place and destination announcer. It is a fifth active RP Ideas product, published as a standalone release outside the existing four-app release batch.

## Download the stable release

- [RP-Android-Apps release](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.1.0) — primary updater source.
- [InternetRadio release](https://github.com/universalrp2003/InternetRadio/releases/tag/oor-arivippu-v1.1.0) — verified fallback.
- [Release notes](../releases/oor-arivippu-v1.1.0.md) · [published APK size and SHA-256 ledger](../docs/published-release-digests.md).

Both public releases carry `Oor-Arivippu-v1.1.0.apk`, 12,211,781 bytes, SHA-256 `34858e8c6e4831e725ac7503f6613c96bcb896d659ebbdb588dcd139afc7c8b3`. Standalone release [run 39105539892](https://github.com/universalrp2003/InternetRadio/actions/runs/39105539892) (commit `fbdcc1b`) passed the release unit tests, lint, signed build and package/version/minimum-SDK/signature checks, then verified that the two public assets have identical size and digest at canonical URLs. Before publication, the Oor test workflow passed on the 1.1.0 source in [run 39105368624](https://github.com/universalrp2003/InternetRadio/actions/runs/39105368624). At the owner's request, 1.1.0 was published as soon as these automatic checks passed, without a device test; see **1.1.0** below for what changed.

The previous stable release, 1.0.2 / code 4, remains available unchanged on both repositories: `Oor-Arivippu-v1.0.2.apk`, 12,195,401 bytes, SHA-256 `bffdf37f525becc3a631c83988e56ca9881e620b76fd2e60bd7ce5b31efdd78b`. Standalone release [run 38840264469](https://github.com/universalrp2003/InternetRadio/actions/runs/38840264469) passed the release unit tests, lint, signed build and package/version/minimum-SDK/signature checks, then verified that the two public assets have identical size and digest at canonical URLs; a separate post-publication API check confirmed the same. Before publication, the Oor test workflow passed on the exact 1.0.2 source in [run 38840000191](https://github.com/universalrp2003/InternetRadio/actions/runs/38840000191).

1.0.2 shortens place and district announcements to the place names alone, without the repeated filler words: for example “Alangudi.” instead of “Entering Alangudi.”, and “ஆலங்குடி.” instead of “தற்போது ஆலங்குடி ஊருக்குள் வந்துள்ளீர்கள்.” The Tamil next-area form (“…, அடுத்த பகுதி …”) is unchanged. At the owner's request, 1.0.2 was published without a device test; no build of the 1.0.2 source has been installed on a phone yet.

The previous stable release, 1.0.1 / code 3, remains available unchanged on both repositories: `Oor-Arivippu-v1.0.1.apk`, 12,195,397 bytes, SHA-256 `ec10c7369807748c9a5608f1a5c6a37eff5f8e1f7433cc16e984c75d8958457d`, published by standalone [run 38826899278](https://github.com/universalrp2003/InternetRadio/actions/runs/38826899278) after Oor test [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762) passed. It added Plus Code/coordinate filtering with normalized place-name comparison and separate Tamil/English Android TTS voice choices. The owner installed the debug test APK from [run 38776902130](https://github.com/universalrp2003/InternetRadio/actions/runs/38776902130), built from the same app source before the version was raised, and reported that it works: screenshots show Play Protect's “This app looks safe” screen, the About page at 1.0.0, the voice picker listing on-device and Internet voices, and a current-place announcement. That build reported 1.0.0. After publication, the owner also installed the debug APK of the exact 1.0.1 source from [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762); screenshots show package 1.0.1 (3), Play Protect's “This app looks safe” screen, the voice and settings screens, and the updater reporting installed 1.0.1 with no newer release. Both builds were debug-signed, so their bytes necessarily differ from the stable APK; the exact published 1.0.1 bytes have not been device-tested.

The first stable release, 1.0.0 / code 2, remains available unchanged on both repositories: `Oor-Arivippu-v1.0.0.apk`, 12,179,013 bytes, SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`, published by standalone [run 38748688974](https://github.com/universalrp2003/InternetRadio/actions/runs/38748688974). The owner reported two-device testing of 1.0.0; those screenshots showed the v1.0.0 (code 2) package, Play Protect's “This app looks safe” screen and the running app, but not the APK SHA-256.

Earlier test APKs were debug-signed. Android may not allow a debug-signed test installation to update to the stable RP Ideas signer. If Android rejects the update, uninstall the test APK before installing stable; uninstalling removes that test installation's local settings and announcement history. Android still controls its per-app **Install unknown apps** permission, confirmation screen and Play Protect decision. The app does not install silently or bypass those checks.

## Connectivity and offline reminders

When internet access is unavailable, GPS tracking may continue, but place-name, district, highway/landmark and destination speech announcements are paused. The interface and ongoing notification state that the internet is unavailable. If enabled, an offline-status reminder is sent immediately when tracking starts offline or connectivity is lost, then at the selected interval: **Off, 5, 10, 15 or 30 minutes** (5 minutes by default from 1.1.0; 15 before). It uses an installed on-device TTS voice when available, otherwise a short local beep; it does not use online voice services. Destination lookup and place names depend on Android's Geocoder and may also fail when its service is unavailable.

## Device-test checklist

- Choose up to four voices offered by the Android TTS engine for Tamil or English. Voice labels identify whether Android marks each as on-device or Internet-dependent; the system default remains available. Network voices may send spoken text to their TTS provider. Offline reminders still force an on-device voice or a beep.
- Use **Test Voice** in both Tamil and English. The UI reports whether the selected voice is available, whether it fell back to the other language, or whether the selected voice is no longer available. If needed, install voice data in the device's Text-to-Speech settings and reopen the app to refresh the list.
- To track a trip, grant foreground location and (on Android 13+) notification permissions, then start tracking while the app is visible. Precise location is recommended; approximate location is accepted but may identify places less accurately.
- On Android 10+, the app offers a separate, optional background-location step. For the full screen-off test, grant **Allow all the time** (Android 11+ may send you to App info/Location settings). You can continue without it, but recovery and background behavior can vary by Android version and OEM. The app does not request automatic boot-start permission.
- Confirm the ongoing notification appears and its stop action stops tracking. Also test the in-app OFF button. On OPPO and Xiaomi, note any battery/background restrictions that stop or delay the service; do not disable OEM safeguards globally just for the test.
- Confirm Plus Codes such as `9X5H+3HF`, coordinate-only labels, and numeric address fragments such as `1/242` are not announced as place names. A Plus Code changing within Alangudi should stay silent; a real named change such as **Alangudi Hospital → Alangudi Bus Stand** should still announce when same-city announcements are enabled. Long multi-stop road descriptions should not replace a named place, and the speed/motion label should appear on separate lines.
- Test connectivity deliberately while tracking: switch off Wi-Fi and mobile data. Confirm the UI/notification clearly says there is **no internet**, place/district/highway/destination speech stops, and the selected offline reminder still uses local TTS or a beep. GPS status can continue updating. Reconnect and check that place lookup resumes. Android's network-validation signal and geocoder service can vary, so report any false status or lookup failure.
- Set a destination while online using a town/city name. Confirm the 10 km, 5 km, 2 km and approximately 500 m arrival alerts, then clear or change it. From 1.1.0 every match is listed with its district, state, PIN code and distance, and the person chooses the right one (many villages share a name); **View on map** opens the chosen place in a maps app. Destination speech remains paused without internet; the separate offline-status reminder may continue.
- Test a phone or VoIP call while tracking. The app uses Android's audio mode as a privacy-minimal, best-effort call signal; it should pause new speech and speak the latest queued place after the mode returns to normal. Some call apps/OEMs may not expose a detectable mode.
- The optional headset **Next/skip** media-button trigger is off by default. Enable it in settings, start tracking, and test the headset button. Media-button routing varies by headset/OEM and may conflict with music controls; disable it if it does.
- District alerts use the Geocoder's `subAdminArea`; highway/toll/junction announcements occur only when returned road/feature names match recognized highway or landmark terms. Neither is a complete offline map database, and some locations will not provide these fields.
- Observe the displayed adaptive GPS interval: about 45 seconds while stationary, 15 seconds in ordinary traffic, and 5 seconds at highway speeds. These are requested intervals, not a measured battery-saving percentage; Android/OEM batching, GPS quality and movement detection can change real behavior. Reverse geocoding is limited to at most once per 30 seconds and generally requires at least 100 m displacement.
- Continuous GPS tracking can still use substantial battery. Stop tracking when finished.

## 1.1.0 (owner requests of 16 and 18 October 2026)

- Never announces geocoder placeholders such as “Unnamed Road” / “பெயரில்லாத சாலை”, a bare “Road”/“சாலை” or a highway “Service Road”, nor the state or country as a town or area. Door numbers (“12/3, …”) and PIN codes are removed from names.
- A Tamil name and its English spelling count as one place (“ஆலங்குடி” then “Alangudi” is not announced twice): names are compared by their consonant sounds when the scripts differ, and must match exactly within one script. Near a town border, a town announced in the last 10 minutes is not announced again.
- The district word is spoken once even when the geocoder's name already contains it (“Karur District” → “Karur district.”).
- Destination: every match is listed with district, state, PIN code and distance; the chosen place's name (not the typed text, such as “pallapatti/karur”) is spoken in the alerts; **View on map** checks it.
- Defaults: next area within the same town **off**; no-internet reminder every **5 minutes**. Choices made earlier are kept.
- Voices: every Android voice of the language is offered, best quality first, with its quality shown; choosing a voice plays it; speech speed slow/normal/fast; a button opens Android's voice downloads.
- Long explanations are folded behind **விவரம் / Details**, and each language is chosen on one compact line.
- About lists the new RP Ideas app **இரகசிய செய்தி (Secret Message)**.

The owner asked to publish 1.1.0 as soon as the automatic checks pass. Oor test [run 39105368624](https://github.com/universalrp2003/InternetRadio/actions/runs/39105368624) passed on the 1.1.0 source: the bilingual UI and About-link check, the unit tests, Android lint and the debug build.

## Build and CI

```bash
./gradlew :oorarivippu:testDebugUnitTest :oorarivippu:lintDebug :oorarivippu:assembleDebug
./gradlew :oorarivippu:testReleaseUnitTest :oorarivippu:lintRelease :oorarivippu:assembleRelease
```

The dedicated [test-APK workflow](../.github/workflows/oorarivippu-test-apk.yml) uploads a short-lived debug artifact; it is for testing, not the stable updater. Stable releases are built and checked separately by the [standalone release workflow](../.github/workflows/oorarivippu-release.yml), which publishes only after an explicit authorized release trigger.

CI runs unit tests, Android lint and APK checks. It does **not** emulate GPS, phone calls, Bluetooth media buttons, TTS, network loss or long-trip battery use, and it does not replace on-device validation. See [`../PRIVACY.md`](../PRIVACY.md) for location and Geocoder data handling.
