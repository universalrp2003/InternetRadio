# Project guidance and owner decisions

## Active project scope

- Maintain five active Android products: `cleaner` (Live Guard / CleanSweep), `radio` (Ramesh Radio), `equalizer` (PulseEQ), `vocalremover` (RP Vocal Remover Studio), and `oorarivippu` (Oor Arivippu / Place Announcer). Oor Arivippu is owner-approved for its first stable release as a separate product and release workflow; do not add it to the existing four-app batch without revising that workflow's tag/version protections.
- The isolated `secretmessage` preview remains a non-shipping local-only development target, not an active released app or secure messenger. Integrate only approved WhisperPad product modules after verifying their real boundaries; preserve MLS, signing and release requirements. Do not claim secure messaging or equate preview expiry with recipient-view expiry.
- Retired standalone app modules have been removed from the active build, CI and store metadata at the owner's request. Preserve their already-published GitHub Releases, tags and APK assets as historical records; do not rebuild or publish them.
- The owner chose **RP Ideas** as the umbrella brand and `RP-Android-Apps` as the destination repository slug. The four legacy bridge clients check `RP-Android-Apps` first and keep `InternetRadio` as a verified fallback during the grace period; older installed clients still require the exact `InternetRadio` URL and reject redirects. Follow `docs/repository-migration.md`; do not rename, archive or delete the old repository until all four bridge updates and the later updater-only transition are confirmed. Oor Arivippu 1.0.0 was separately published to both repositories; its standalone release is not part of that four-app migration batch.
- The owner reported that the four existing apps worked on their phone on 13 October 2026. On 15 October, the owner reported successful Oor Arivippu testing on two devices, authorized publication, and later confirmed the stable app is working. Owner-provided screenshots show the 1.0.0 (code 2) package, Play Protect's “This app looks safe” screen, and the running app's version/update status. This is owner-reported device feedback, not independent certification or hash-linked validation: the screenshots do not show the APK SHA-256. Do not claim the exact stable APK bytes were device-tested unless that hash is matched.

## Shared app and release requirements

- The five active app UIs are Tamil-first with an in-app English option. When adding visible text, add both translations, preserve dynamic placeholders, and run the localization checks.
- Do not label any contributor “Author.” About pages credit shared contributors accurately, include Ramesh prathap .R (`universalrp2003@gmail.com`) and Arena.ai Agent Mode as software assistance (no invented contact), and link to the current release pages.
- Use the verified stable GitHub Releases updater. Check official release sources, offer only a newer stable version, verify official asset URL, size, SHA-256, package/version and pinned signing certificate, then hand off to Android. Never install silently, bypass Play Protect, or claim that the app can grant/revoke Android's per-app “Install unknown apps” access temporarily.
- Oor Arivippu uses package `com.oorarivippu.voiceannouncer`, version 1.0.0 / code 2 for its first stable release, the existing pinned RP Ideas signing identity, and a standalone release workflow gated by an explicit `[publish-oorarivippu]` commit on the authorized branch (or a future authorized manual dispatch). Prior test APKs were debug-signed; if Android rejects an update because of the signer, explain that the test build must be uninstalled before installing the stable build. Do not overstate exact-byte device testing.
- Oor Arivippu must continue to suppress place/destination announcements when internet is unavailable. Its selectable offline-status reminders are Off/5/10/15/30 minutes (default 15); use an on-device TTS voice when a local voice exists, otherwise a short local beep. Do not use network TTS for offline reminders.
- Keep package IDs, signing identity and existing released asset bytes unchanged. Use a new app version/tag for changed APK bytes; never overwrite an existing version's asset.
- The Live Guard release permission-delta gate is mandatory. A device evidence record is required when a release adds a dangerous permission; fail closed if the previous release cannot be inspected. App-op/signature permissions are not runtime-dangerous permissions. If evidence exists, publish only the exact tested APK bytes. Never describe CI as a phone test or claim Play Protect approval.
- The repository currently supports direct APK sideloading. Any store listing or certification claim must be separately verified.

## Oor Arivippu (Place Announcer)

- Keep connectivity-loss status explicit, suppress place/destination speech while offline, and offer only the configured periodic offline-status reminder. The reminder uses a verified local TTS voice or a short notification-stream beep; it must not invoke online geocoding/voice for offline-status speech.
- Keep tracking user-controlled and visible in the ongoing foreground notification. Preserve the optional headset “Where am I?” control and avoid announcing while call audio is active.
- The service field declaration is `private val travelDetector = TravelStateDetector()`; preserve that exact declaration when making related edits.
- About content must include the project and AI-assistance credits, source/download repository links, current app release links, and the verified updater. Keep the updater language synchronized with the app's Tamil/English preference.

## Live Guard (CleanSweep)

- Preserve the no-notification/message-reading production design. Do not restore the retired notification listener, Accessibility, SMS, contacts/call-log, or device-admin capabilities without a fresh owner decision and policy/device review. Do not bypass or disable Play Protect to address an install block.
- Show battery, CPU, RAM, network and age estimates only from truthful available measurements; label OEM-dependent/unavailable readings honestly. A saved review date must be a real completed review, never invented by opening a screen.
- The 44 °C owner setting is a warning, not an automatic pause threshold. Do not add a processing pause at that temperature without separate rationale.
- Respect opt-in monitoring/history, saved OFF settings and the current compact Battery/Data/Phone launcher-widget choices. Do not silently enable persistent monitoring, permissions or deletion.
- Home news is for the phone's local day with real publication timestamps; do not synthesize “today” headlines or repeat read items automatically. Keep security review separate from news.
- AI/web requests must be bounded, cancellable and user-controlled, with explicit disclosure of what leaves the phone. Do not invent current prices, live-web success, audible speech or OEM background guarantees. Keep the confirmed voice behavior: alternate the two selected/previewed voices between answers, read each answer once, and never infer gender from Android voice metadata.

## Audio apps

- **RP Vocal Remover Studio:** never upload selected audio or use an external audio-processing service. Model delivery over the internet is allowed; processing stays on-device. The first pass creates an instrumental WAV and never alters the original. A second reduction pass is optional and user-started after listening. Offer separate 320 kb/s CBR MP3 export, disclose that MP3 is lossy, and retain the WAV.
- Keep Slow/Medium/Fast on the main processing screen; support pause/resume and durable long jobs. Low-RAM pauses may auto-resume only after recovery; user, thermal, storage and Android service-limit pauses remain manual. Use a foreground service/wake lock for background processing, with a keep-screen-awake option; do not promise Android battery-optimization exemptions.
- Report CPU temperature, RAM and load only when available, and label OEM-dependent/missing values. Do not apply the Live Guard 44 °C warning as an automatic pause threshold.
- **Ramesh Radio / PulseEQ:** process local audio locally. Describe any external-player or system-wide effect as device/ROM-dependent; do not imply universal system-wide equalization.

## Verification before release

- Run `python3 tools/verify_cleaner_localization.py`, `python3 tools/verify_apps_localization.py`, `python3 tools/verify_cleaner_core_manifest.py`, `python3 tools/verify_cleaner_next_update_source.py`, and `python3 -m unittest discover -s tools/tests -p 'test_*.py' -v` when relevant. Also run Oor Arivippu unit tests, lint, and a signed release build for its standalone release.
- CI must build the four legacy batch modules, run their unit tests and required CleanSweep regression suites, verify package/version/minimum SDK/permissions/signature/assets, and compare GitHub release-asset sizes and SHA-256 values before and after publication. Oor Arivippu is verified/published by its dedicated workflow; require both repositories to serve the same signed APK before considering publication complete.
- Give an honest validation summary and call out any remaining real-device tests. User reports, static checks, CI builds and release metadata are distinct kinds of evidence.
