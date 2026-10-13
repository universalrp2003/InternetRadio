# Project guidance and owner decisions

## Active project scope

- Maintain exactly these four active Android modules: `cleaner` (Live Guard / CleanSweep), `radio` (Ramesh Radio), `equalizer` (PulseEQ) and `vocalremover` (RP Vocal Remover Studio).
- Retired standalone app modules have been removed from the active build, CI and store metadata at the owner's request. Preserve their already-published GitHub Releases, tags and APK assets as historical records; do not rebuild or publish them.
- The owner chose **RP Ideas** as the umbrella brand and `RP-Android-Apps` as the future repository slug. Keep the existing `InternetRadio` repository URL for now: current installed update clients require it and do not follow repository-rename redirects. Follow `docs/repository-migration.md`; do not rename or delete the current repository before bridge updates for all four apps are installed and verified.
- On 13 October 2026 the owner reported installing all four current apps and that they worked on their phone. This is owner-reported device feedback, not a certification for every device or Android version.

## Shared app and release requirements

- All four user interfaces are Tamil-first with an in-app English option. When adding visible text, add both translations, preserve dynamic placeholders, and run the localization checks.
- Do not label any contributor “Author.” About pages credit shared contributors accurately, include Ramesh prathap .R (`universalrp2003@gmail.com`) and Arena.ai Agent Mode as software assistance (no invented contact), and link to the four current release pages.
- Use the verified stable GitHub Releases updater. Check on launch, offer an update only for a newer stable release, verify official asset URL, size, SHA-256, package/version and pinned signing certificate, then hand off to Android. Never install silently, bypass Play Protect, or claim that the app can grant/revoke Android's per-app “Install unknown apps” access temporarily.
- Keep package IDs, signing identity and existing released asset bytes unchanged. Use a new app version/tag for changed APK bytes; never overwrite an existing version's asset.
- The Live Guard release permission-delta gate is mandatory. A device evidence record is required when a release adds a dangerous permission; fail closed if the previous release cannot be inspected. App-op/signature permissions are not runtime-dangerous permissions. If evidence exists, publish only the exact tested APK bytes. Never describe CI as a phone test or claim Play Protect approval.
- The repository currently supports direct APK sideloading. Any store listing or certification claim must be separately verified.

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

- Run `python3 tools/verify_cleaner_localization.py`, `python3 tools/verify_apps_localization.py`, `python3 tools/verify_cleaner_core_manifest.py`, `python3 tools/verify_cleaner_next_update_source.py`, and `python3 -m unittest discover -s tools/tests -p 'test_*.py' -v` when relevant.
- CI must build all four active modules, run their unit tests and the required CleanSweep regression suites, verify package/version/minimum SDK/permissions/signature/assets, and compare GitHub release-asset sizes and SHA-256 values before and after publication.
- Give an honest validation summary and call out any remaining real-device tests. User reports, static checks, CI builds and release metadata are distinct kinds of evidence.
