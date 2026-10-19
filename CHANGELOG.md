# Changelog — RP Ideas Android apps

This file indexes the five actively maintained apps. App-specific details, installation notes and verified APK digests are linked below. Historical GitHub Releases, tags and assets remain available even when no longer represented in the active source tree.

## Unreleased

### Ramesh Radio 1.12

- **Fix — crash when opening the live station list (நேரடி நிலையப் பட்டியல்).** The directory
  can return the same stream URL twice (relays, the same station under two names). The list
  used the URL as its Compose key, and a duplicate key throws
  `IllegalArgumentException: Key <url> was already used`, which closed the app. That is why
  it only happened *sometimes*: it depended on the answer the directory happened to send.
  `DirectoryClient` now de-duplicates by URL, the visible list is de-duplicated too, and the
  directory list key is `url + name` so a future duplicate cannot take the app down again.
- **New — draggable seek bar in Now Playing.** Recorded audio (local files) seeks anywhere in
  the file; live radio shows a LIVE pill and moves only inside the buffer, because a live
  stream has no "forward".
- About now lists **all six apps** from one shared list (`SisterApps`) — the old hard-coded
  four pointed at the `InternetRadio` repository and left out Oor Arivippu and Secret Message.

### Live Guard 2.47

- **New — 4G and 5G counted separately.** Android keeps one mobile byte counter, so the app
  samples it every 3 seconds and files each delta under the radio that was active. A new
  `RadioWatchService` (foreground, `dataSync`, started only when the feature is on) does this
  and sends the ongoing notification "4G x left · 5G Unlimited".
- **New — 4G ⇄ 5G announcements**, Tamil and English, with their own switch and a 20-second
  minimum gap so a flapping modem cannot narrate every hop. Wi-Fi ⇄ mobile stays a separate
  switch.
- **New — in-app volume slider** for everything the app speaks (0–100 %), applied per
  utterance with `KEY_PARAM_VOLUME`; the phone's own volume is never changed. The old "Louder
  voice" switch still works and is used as the default when the slider has never been moved.
- **New — 5G-unlimited switch (Auto / On / Off)** with the operator's real terms: Jio needs
  ₹349+ **and** 2 GB/day; Airtel needs an eligible ₹349+ pack, the one-time claim in the
  Airtel Thanks app, and is capped at 300 GB per 28-day cycle. The card shows the reason.
- New permission: `FOREGROUND_SERVICE_DATA_SYNC` (Android 14+ requires the service type to be
  declared). `READ_PHONE_STATE` was already granted.
- About now lists all six apps from the shared `SisterApps` list.

### All apps

- One shared list of the six apps (`shared/.../SisterApps.kt`). Oor Arivippu and Secret
  Message already showed six; Live Guard, Ramesh Radio, PulseEQ and Vocal Remover showed
  four with stale `InternetRadio` links.

## Oor Arivippu 1.0.2 — 16 October 2026

- Place and district announcements speak only the place names. The repeated filler is gone: English no longer says “Entering”, and Tamil no longer says “தற்போது … ஊருக்குள் வந்துள்ளீர்கள்” or “… பகுதிக்கு வந்துள்ளீர்கள்” (for example “Alangudi.” / “ஆலங்குடி.”, and “Pudukkottai district.” / “புதுக்கோட்டை மாவட்டம்.”). The Tamil next-area form with “அடுத்த பகுதி” is unchanged, as are announcement timing, offline suppression and reminders, Plus Code filtering and the voice choices. No permissions changed. See [`releases/oor-arivippu-v1.0.2.md`](releases/oor-arivippu-v1.0.2.md).
- Published by standalone [GitHub Actions run 38840264469](https://github.com/universalrp2003/InternetRadio/actions/runs/38840264469) from commit `50bd12b2a2b205d5adfce7bb70b47df23b2ccc16`, after the Oor test workflow passed on the identical app source in [run 38840000191](https://github.com/universalrp2003/InternetRadio/actions/runs/38840000191). The workflow and a separate public-API check confirmed that both repositories serve the same stable APK (`Oor-Arivippu-v1.0.2.apk`, 12,195,401 bytes, SHA-256 `bffdf37f525becc3a631c83988e56ca9881e620b76fd2e60bd7ce5b31efdd78b`) at canonical release and download URLs. The 1.0.0 and 1.0.1 releases and assets are unchanged.
- At the owner's request, 1.0.2 was published without a device test; no build of the 1.0.2 source has been installed on a phone.

## Oor Arivippu 1.0.1 — 16 October 2026

- Oor Arivippu filters Plus Codes and coordinate-only labels, compares normalized place names before speaking, prefers named landmarks such as hospitals/bus stands, and offers up to four Android TTS voices per language where available. Network voices are identified; offline reminders continue to use a local voice or beep. No permissions changed. See [`releases/oor-arivippu-v1.0.1.md`](releases/oor-arivippu-v1.0.1.md).
- Published by standalone [GitHub Actions run 38826899278](https://github.com/universalrp2003/InternetRadio/actions/runs/38826899278) from commit `6f14f65c7cb54ab48c1a3460bab73fb75178951c`, after the Oor test workflow passed on the identical app source in [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762). The workflow and a separate public-API check confirmed that both repositories serve the same stable APK (`Oor-Arivippu-v1.0.1.apk`, 12,195,397 bytes, SHA-256 `ec10c7369807748c9a5608f1a5c6a37eff5f8e1f7433cc16e984c75d8958457d`) at canonical release and download URLs. The 1.0.0 releases and assets are unchanged.
- The owner device-tested the debug APK from [run 38776902130](https://github.com/universalrp2003/InternetRadio/actions/runs/38776902130), built from the same app source before the version was raised, and authorized this release. That build reported 1.0.0. After publication, the owner also installed the debug APK of the exact 1.0.1 source from Oor test [run 38826564762](https://github.com/universalrp2003/InternetRadio/actions/runs/38826564762); it reported 1.0.1 (3), passed Play Protect's “This app looks safe” check and showed no newer release, and the owner reports it works. Both test builds were debug-signed; the exact 1.0.1 stable bytes were not device-tested.

## Latest published releases — as of 16 October 2026

| App | Version | Release notes | RP-Android-Apps | InternetRadio fallback |
|---|---:|---|---|---|
| Oor Arivippu | 1.0.2 / code 4 | [`releases/oor-arivippu-v1.0.2.md`](releases/oor-arivippu-v1.0.2.md) | [oor-arivippu-v1.0.2](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/oor-arivippu-v1.0.2) | [oor-arivippu-v1.0.2](https://github.com/universalrp2003/InternetRadio/releases/tag/oor-arivippu-v1.0.2) |
| Live Guard (CleanSweep) | 2.46 / code 51 | [`releases/live-guard-v2.46.md`](releases/live-guard-v2.46.md) | [live-guard-v2.46](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/live-guard-v2.46) | [live-guard-v2.46](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.46) |
| Ramesh Radio | 1.11 / code 11 | [`releases/ramesh-radio-v1.11.md`](releases/ramesh-radio-v1.11.md) | [ramesh-radio-v1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/ramesh-radio-v1.11) | [ramesh-radio-v1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.11) |
| PulseEQ | 1.5 / code 6 | [`releases/pulseeq-v1.5.md`](releases/pulseeq-v1.5.md) | [pulseeq-v1.5](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/pulseeq-v1.5) | [pulseeq-v1.5](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.5) |
| RP Vocal Remover Studio | 0.1.11 / code 12 | [`releases/rp-vocal-remover-studio-v0.1.11.md`](releases/rp-vocal-remover-studio-v0.1.11.md) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/RP-Android-Apps/releases/tag/rp-vocal-remover-studio-v0.1.11) | [rp-vocal-remover-studio-v0.1.11](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.11) |

GitHub Actions [run 38521705254](https://github.com/universalrp2003/InternetRadio/actions/runs/38521705254) passed the four-app build, unit/regression tests, APK and signature checks, and dual-repository publication. Both repositories now serve the four stable APKs. A live public-API check confirmed the destination releases are non-draft/non-prerelease, use canonical release and download URLs, are newer than the bridge versions, and have exactly matching asset sizes and SHA-256 values on both repositories. Owner-provided phone screenshots show update-available notices for all four bridge versions, confirming device-side metadata discovery. They do not show a completed download/install of these exact bytes or identify the supplying repository. The updater source order and selection logic prefer `RP-Android-Apps`. See [`docs/published-release-digests.md`](docs/published-release-digests.md). The build-only hashes in [`docs/candidate-build-digests.md`](docs/candidate-build-digests.md) are from a different run and must not be treated as the published asset digests.

Oor Arivippu's first stable release, 1.0.0 / code 2 ([`releases/oor-arivippu-v1.0.0.md`](releases/oor-arivippu-v1.0.0.md)), was then published separately, outside that four-app batch, by standalone [GitHub Actions run 38748688974](https://github.com/universalrp2003/InternetRadio/actions/runs/38748688974). The public API confirmed that both repositories serve the same stable APK (`Oor-Arivippu-v1.0.0.apk`, 12,179,013 bytes, SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`) at canonical release and download URLs. The owner reports successful two-device testing and that the app is working. Supplied screenshots show the v1.0.0 (code 2) package and the app running at v1.0.0, but do not provide the APK SHA-256 or link the installed bytes to the published digest.

## Previous stable bridge releases — 14 October 2026

| App | Version | Release notes | GitHub Release |
|---|---:|---|---|
| Live Guard (CleanSweep) | 2.45 / code 50 | [`releases/live-guard-v2.45.md`](releases/live-guard-v2.45.md) | [live-guard-v2.45](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.45) |
| Ramesh Radio | 1.10 / code 10 | [`releases/ramesh-radio-v1.10.md`](releases/ramesh-radio-v1.10.md) | [ramesh-radio-v1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.10) |
| PulseEQ | 1.4 / code 5 | [`releases/pulseeq-v1.4.md`](releases/pulseeq-v1.4.md) | [pulseeq-v1.4](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.4) |
| RP Vocal Remover Studio | 0.1.10 / code 11 | [`releases/rp-vocal-remover-studio-v0.1.10.md`](releases/rp-vocal-remover-studio-v0.1.10.md) | [rp-vocal-remover-studio-v0.1.10](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.10) |

Publication run **38501139771** built and verified the four bridge APKs and published them on the old `InternetRadio` endpoint so existing installations could discover an increasing-version update. Those earlier release pages, tags and assets remain available and were not overwritten by the new batch.

The Live Guard permission-delta check for 2.45 found no newly added dangerous permission, so no physical-device evidence record was required. The exact CI-generated bridge APK bytes were not installed on a phone during that publication; no exact-build device test is claimed. CI and release validation are not Play Protect approval or an all-device compatibility claim. On 14 October, the owner reported the bridge apps were working and manual update checks completed without errors. The subsequent 2.46 / 1.11 / 1.5 / 0.1.11 batch has now seeded `RP-Android-Apps`; API/source-selection verification is recorded above and in the migration ledger. On-device retrieval/installation of those latest exact bytes remains a separate grace-period check.

Full APK filenames, byte sizes and SHA-256 digests for the current and previous releases are in [`docs/published-release-digests.md`](docs/published-release-digests.md).

## Previous stable releases — 13 October 2026

| App | Version | Release notes | GitHub Release |
|---|---:|---|---|
| Live Guard (CleanSweep) | 2.44 / code 49 | [`releases/live-guard-v2.44.md`](releases/live-guard-v2.44.md) | [live-guard-v2.44](https://github.com/universalrp2003/InternetRadio/releases/tag/live-guard-v2.44) |
| Ramesh Radio | 1.9 / code 9 | [`releases/ramesh-radio-v1.9.md`](releases/ramesh-radio-v1.9.md) | [ramesh-radio-v1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/ramesh-radio-v1.9) |
| PulseEQ | 1.3 / code 4 | [`releases/pulseeq-v1.3.md`](releases/pulseeq-v1.3.md) | [pulseeq-v1.3](https://github.com/universalrp2003/InternetRadio/releases/tag/pulseeq-v1.3) |
| RP Vocal Remover Studio | 0.1.9 / code 10 | [`releases/rp-vocal-remover-studio-v0.1.9.md`](releases/rp-vocal-remover-studio-v0.1.9.md) | [rp-vocal-remover-studio-v0.1.9](https://github.com/universalrp2003/InternetRadio/releases/tag/rp-vocal-remover-studio-v0.1.9) |

CI publication run **38380663566** built and verified those releases, including CleanSweep regression tests, package/version/signature checks and public-asset size/SHA-256 verification. The Live Guard permission-delta check found no newly added dangerous permission relative to 2.43. The exact Live Guard CI release APK was not installed during that run; CI is not a phone test or Play Protect approval. The owner separately reported installing all four then-current apps and that they worked on their phone on 13 October 2026; that report is not an all-device compatibility claim. Their release pages, tags, APK assets and original SHA-256 records remain unchanged as historical releases.

## Recent Live Guard history

- **2.44 / code 49:** added update verification, safer APK hand-off and contributor/release links. The permission-delta gate found no new dangerous permission relative to 2.43. See [`releases/live-guard-v2.44.md`](releases/live-guard-v2.44.md).
- **2.43 / code 48:** corrected charging measurements so unsupported zero current is unavailable rather than a false zero-watt reading. See [`releases/v2026.10.12-3.md`](releases/v2026.10.12-3.md).
- **2.42 / code 47:** device-health, voice-selection and monitoring refinements; associated device evidence and scope are linked from the release notes. See [`releases/v2026.10.12-2.md`](releases/v2026.10.12-2.md).
- **2.41 / code 46:** honest charging readings, a local 44 °C warning, and bounded AI output/provider checks. The temperature warning is not an automatic pause threshold. See [`releases/v2026.10.12-1.md`](releases/v2026.10.12-1.md).
- **2.40 / code 45:** removed notification/message-reading capability from the app. See [`releases/v2026.10.11-4.md`](releases/v2026.10.11-4.md) and the [Play Protect installation record](docs/live-guard-play-protect-install-block.md).

Older release notes and device-test records remain in `releases/` and `docs/` as historical records. They describe the scope and test process at the time of those versions and are not current feature or build instructions.

## Project scope

The current source, Gradle build and CI cover five active apps: Live Guard, Ramesh Radio, PulseEQ, RP Vocal Remover Studio and Oor Arivippu. The existing four-app batch workflow remains separate from Oor Arivippu's standalone release workflow; store-art preparation currently covers the three apps with store metadata. Releases for retired modules are preserved on GitHub as history and are not part of current builds or future release batches. The repository slug stays unchanged until the staged updater migration is complete; see [`docs/repository-migration.md`](docs/repository-migration.md).
