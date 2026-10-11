# Privacy policy — CleanSweep, Ramesh Radio, Internet Radio, AppForge, PulseEQ

**Last updated: 11 October 2026** (2.40 core candidate: notification reader removed)
**Developer: Ramesh prathap .R — universalrp2003@gmail.com**

## The short version

These apps are built to work on your phone, not on a server. There is no developer-run server,
no account to create, no advertising, no analytics, and no developer-side tracking. Optional
user-controlled local histories stay on your phone (described below). Nothing is
uploaded to the developer, and the developer cannot see anything on your phone.

## What is collected by the developer

**Nothing.** No personal data, no device identifiers, no location, no contacts, no files, no
usage statistics. There is no backend to receive them; the developer has no way to collect
them.

## Pending source changes (next APK build/publication on hold)

These changes are being prepared, not shipped in the verified2.37 APK:

- A single app-private last permission-review aggregate stores the real completed
  scan time, counts and an incomplete flag. No app/package/finding identities or
  timeline are added to this cache. Reading it does not create a review date or a
  current safety verdict. It remains until cleared with **Clear last-review summary**,
  app-data removal or uninstall; Android backup stays disabled. Optional detailed
  security history remains opt-in and separate.
- Explicit assistant Voice A/B selections save installed engine voice IDs per
  Tamil/English language and a bounded alternating-answer counter in local settings.
  Previews use the phone's initialized TTS engine; no inferred gender, new recording,
  microphone permission, developer upload or automatic voice-data download is added.
  Network TTS voices may send the spoken answer/preview text to their provider.
  Preview/engine completion does not prove the owner heard it. Answer speech is not
  automatically retried/replayed in another voice.
- Price questions may query public DuckDuckGo HTML search in addition to the
  existing lookup sources; news uses English/Tamil Google News RSS when selected.
  Search hosts receive the question/search query and normal connection metadata,
  not the phone inventory or personal file contents. Excerpts/citations remain
  unverified; fetch time is not current-price or publication-date proof.
- Request timers/progress and ownership IDs are runtime state, not a new saved
  diagnostic/history. AI keys are sent to the selected provider for authentication;
  provider HTML/raw error bodies are not echoed in normal user-facing errors.
  No silent chosen-provider-to-Free cross-provider question/report transmission
  is added. A different provider is an explicit user choice.

See `docs/live-guard-next-update-review.md` for the uncompiled source/test limitations.

## Live Guard 2.37: news, automatic speech and launcher widget

The Home news strip requests public Tamil RSS/Atom feeds (publisher endpoints,
BBC, Oneindia and Google News). Those hosts receive normal connection metadata
such as IP address and app User-Agent, not app inventory, prompts or personal files.
Cached headline/category/source/link/publication-time metadata is bounded to80
items. Up to160 normalized seen-title keys are stored for the current local-day/
timezone scope to prevent automatic repetition; an expired scope is cleared on
next access. No saved security reminder is used as a news filler. Opening an
article or reading it aloud is explicit. The news text is not independently verified.

Assistant identity/configuration questions are answered locally from public
provider/model labels, without a web/API request; these labels do not prove a
particular model ran. Other online chat/web-lookup disclosures below still apply.

Automatic voice diagnostics keep **one last event type, stage and timestamp** in
app-private preferences, even when optional histories are off. Runtime status can
show language availability and current media volume, but does not append these
values, utterance text, audio, prompts, keys, network identifiers or engine-private
IDs to the saved diagnostic. This is not an opt-in historical event log or an
allowlisted diagnostic export. Engine callbacks cannot establish audible output.
The already-saved daily brief remains separate; its save time is now recorded for
a dated secondary widget display.

Speech uses the phone's installed TTS engine. An online voice can send the text
being spoken (including enabled alert wording) to that engine's provider under its
privacy policy; there is no developer audio recording. Offline retry requires an
installed voice in the same language; availability is not guaranteed. “Louder”
changes utterance amplitude, not phone media-volume/DND settings. No new recording,
voice upload to the developer, system-policy override or microphone permission is added.

A launcher widget stores a small per-widget display-mode preference and displays
available battery/storage/mobile measurements and their snapshot time. Queries
are off-main and use existing permissions; widget refresh does not enable a new
permanent monitor. Short power-event jobs respect enabled voice categories/quiet
hours and do not enable the existing opt-in persistent service. Live listeners
are owned by the visible activity, already-running monitor or its existing visual
fallback job. Runtime listener registration status is displayed, not persisted.
Android8+ manifest power delivery can be missed/deferred with no live owner; Android/OEM
and force-stop restrictions still apply. Extra percentage digits require actual
reported level/scale resolution, not estimated-capacity interpolation. Histories,
phone-age privacy, all-current-issues report controls and app-name/network sharing
choices are unchanged.

## Live Guard 2.36: local phone-age estimate

The Home badge calculates a coarse model/initial-Android-generation estimate from
local Android model/product facts and best-effort factory API properties. The result
is cached only in memory per local calendar day. This badge does not add a permission,
network request, upload, saved history or monitoring service. Restoration does not
append age results, factory API or raw device/product properties to the optional AI
or diagnostic export; the existing diagnostic's allowlisted model/OS facts are
unchanged. The estimate does not reveal this handset's actual manufacture, purchase
or first-use date; unsupported evidence is unavailable, not substituted from an
updated OS/security patch or default year.

## Live Guard 2.35: local history, diagnostics and review choices

The optional **security timeline**, **charging history** and **daily mobile history**
recording switches all default **off**. Enabling one does not enable persistent
monitoring or a new service. Histories use app-private no-backup files and are not
uploaded or synced by these features. Android backup is disabled for Live Guard.

- Security history can contain installed/observed app labels, package/service IDs,
  finding descriptions, check times and reviewed/expected acknowledgements, retained
  up to 200 change events plus the latest comparable baseline. “Newly observed” is
  not a claim of install/grant time; failed/incomplete checks do not resolve old findings.
- Charging history stores reported battery percentage, battery-side watts/temperature,
  wall/elapsed observation times, non-identifying boot-counter availability and partial-session statistics: up to 30 sessions /
  30 days and 240 chart points per session plus its latest sample. No adapter identity,
  personal content or continuous/guaranteed background recording is inferred. Expired
  charging entries are pruned when the app next reads state, not while Android has
  force-stopped the app.
- Mobile history stores device-wide cellular daily byte totals/date/source/coverage,
  up to 90 daily records. The budget tool can read seven Android-reported prior days without
  saving them when recording is off. Today and counter fallbacks are partial;
  unknown values are not stored as invented zero days. Quota and expiry are your
  configuration (inclusive expiry or exclusive next-reset day), not a carrier balance or verified unlimited-5G entitlement.
- Each history tab has **Clear**. Off pauses collection, leaving previous saved data
  until cleared. Clearing security also removes reviewed acknowledgements. Explicit
  review choices are saved when you select them even with passive logging off; they
  never revoke access or certify safety. Uninstalling/removing app data removes the
  app-private state through Android; no developer copy exists.

The **compatibility diagnostic** is an allowlisted local snapshot: app version,
manufacturer/model, Android version/API, check time and known permission/configuration/
sensor-availability status IDs. Preview precedes copy/share. It excludes keys,
installed-app identities, SMS/photos/files, arbitrary preferences, logs/crashes/prompts,
IP/SSID/network identifiers and serial/device identifiers. No automatic upload occurs.
Copy uses Android's clipboard; explicit share sends exactly that preview to the app
**you choose**, whose own handling/privacy policy then applies. The snapshot cannot
prove OEM overlay rendering, settings links or background survival.

The **AI-linked action checklist** has local evidence-bound identities and priorities.
Optional remote AI explanations use opaque step tokens and the existing app-name/
network sharing choices. Histories are not automatically appended to the AI report.
Only trusted local targets select guarded Android settings/tool routes; returned model
URLs, intents, package names, priority/resolution fields or commands are not executed.
No automatic permission revocation, uninstall, cleaning or completion is performed.
AI text remains unverified advice; a fresh local recheck is separate from a review choice.

## When an app uses the internet

Internet access is used for features you start or enable, including optional
background monitoring, daily briefs, news feeds and release checks:

| App | Why it can go online | What is sent |
|---|---|---|
| **Ramesh Radio** | To stream the radio station you chose, and to search the public Radio-Browser station directory when you tap search | The station's stream URL, and the search words you type |
| **Internet Radio** | To stream the station you chose | The station's stream URL |
| **Live Guard / CleanSweep — optional AI reports** | When you tap **Analyse / Ask AI about all current issues**, or enable a configured AI daily brief; your own provider/key or the selected Free option | Phone readings and findings, optional app names/packages/service IDs, available dated malware/junk results (counts and sizes, not paths), and network details only when network sharing is enabled. “See exactly what was sent” shows the full analysis text. Requests go directly to the selected AI provider or a disclosed keyless fallback, never to the developer |
| **CleanSweep — assistant** | Online Ask or optional live web lookup; identity/settings replies are local | The question, phone-care context and web-search query. Do not type secrets. General chat is separate from the security report; security-report findings are not sent to web search |
| **CleanSweep — Home news / TTS** | Visible Home news refreshes; explicit or enabled speech | Public feed requests and normal connection metadata to the selected feed hosts; an online TTS engine may send spoken text to its own provider. Local identity answers do not search or call AI |
| **CleanSweep — malware hash lookup** | When you start the hash scan or check an app | APK SHA-256 hashes to MalwareBazaar and optionally VirusTotal with your key, never APK files. An AI security report may include the dated result and names only when app-name sharing is enabled |
| **CleanSweep — speed test** | Only when you tap the speed test | Nothing but the test traffic itself |
| **CleanSweep — public IP / ping / live quality** | Mobile & data tools, the visible Home readings panel, or enabled live status monitoring | Requests/probes to check services (Cloudflare, ipinfo.io, ipapi.co or ipify). Those services see your connection/IP metadata; no app inventory or files are sent |
| **PulseEQ** | Optional GitHub release checks; audio stays local | GitHub receives normal request metadata, not audio |
| **AppForge** | No change to its existing local project workflow | — |

Search words, station URLs and AI questions are handled by those third parties under their own
privacy policies, because the request goes from your phone directly to them.

## Permissions, and why each one is needed

| Permission | App | Why |
|---|---|---|
| All files access (`MANAGE_EXTERNAL_STORAGE`) | CleanSweep | To find junk, duplicates, old downloads and empty folders. **Nothing is ever deleted without you selecting it and confirming.** |
| Notifications | CleanSweep | Battery, heat and daily-brief messages. Can be switched off in the app. |
| Battery / charging foreground service | CleanSweep | The charging/status card. Charging-only mode stops on unplug; an explicitly enabled background monitor continues the normal data/network view and has a Stop action. |
| Location | CleanSweep | Only on the Wi-Fi and Mobile screens: Android itself hides the Wi-Fi network details, signal strength and cell-tower list without it. CleanSweep never records or transmits your location. |
| Phone state (`READ_PHONE_STATE`) | CleanSweep | Only to show the real network type (5G/4G), operator and signal on the Mobile screen. |
| Usage access | CleanSweep | Only to show per-app cache sizes and data usage; you grant it in system settings and can revoke it there. |
| Display over other apps | CleanSweep, off by default | The optional status-bar pill: watts only while connected, normal data/D/U view with background monitoring while unplugged. |
| Media / audio | Ramesh Radio, PulseEQ | To play your own audio files and to run the equalizer. |
| Internet | CleanSweep, Ramesh Radio, Internet Radio | See the table above. |

CleanSweep does **not** use an Accessibility service: it cannot read your screen and cannot tap
inside other apps. From **2.40** it also declares no notification-listener service, no SMS, Contacts
or call-log access and no device-admin capability — core Live Guard cannot read other apps'
notifications or messages. Its own alerts keep the ordinary Notifications permission above.
Microphone input exists only in the assistant's voice button, which uses
Android's own speech recogniser — no audio is recorded or stored, and there is no `RECORD_AUDIO`
permission. Wi-Fi details can be displayed after the relevant permission is granted.
In AI reports, the network-sharing switch controls sending the available SSID, local
addresses/DNS, device-scan details and network/data observations. Turning it off
omits those fields. The security AI report also honours the app-name switch,
including labels embedded in finding/malware descriptions. It does not send file
contents, file paths, messages, contacts, credentials or GPS coordinates.

## Children

These apps are general-purpose tools, not directed at children. Optional local features process the information
explicitly described in this policy. Live Guard **2.40 removes notification/message reading entirely**
(2.39 shipped it opt-in); no message content is read, stored, translated or submitted to an AI by
the current app.

## Data retention and deletion

The apps keep their settings, your AI key (stored in the app's private storage on your phone),
favourites and the last daily brief on your phone only. Uninstalling the app removes all of it.
Deleting a provider key stops authenticated requests to that provider, not the Free
lane. Pending source makes automatic online daily summaries a separate explicit
choice, OFF by default. Turn off online assistance and that daily choice if you do
not want those requests; a manual report/AI-analysis button is a separate action.
There is nothing on any server for the developer to delete, because nothing was ever sent to
the developer.

## Changes

Any change to this policy will be published in this file in the public source repository, with
the date above updated.

## Contact

Ramesh prathap .R — universalrp2003@gmail.com
Source code and issue tracker: <https://github.com/universalrp2003/InternetRadio>

## Update checks (CleanSweep, Ramesh Radio, PulseEQ)
These apps check GitHub published releases at most once a day on launch, with an off switch and manual check in About/More. GitHub receives normal connection metadata, including your IP address. No app inventory or audio is sent. Opening a release/download is user initiated. PulseEQ now has Internet permission for these checks; audio processing stays on-device.

CleanSweep headline queries use Google News RSS when web lookup is enabled. Google receives the query and connection metadata; fetched headline links open their corresponding news pages.

## Live Guard 2.40 (candidate) — notification/message reading removed

Prepared, **not published and not device-tested**. Why: Google Play Protect blocked
installation of the published APK on the owner's phone, and the leading suspect is the
2.38/2.39 notification-reader capability declared in the package
(`BIND_NOTIFICATION_LISTENER_SERVICE`, the `NotificationListenerService` intent filter).
Removing one capability is not a proven fix. See
[`docs/live-guard-play-protect-install-block.md`](docs/live-guard-play-protect-install-block.md).

- **No notification or message access exists in core Live Guard.** The reader service,
  its listener permission, its intent filter, its screen/route/links and its
  translation-model dependencies are removed. The app declares no notification-listener,
  SMS, Contacts, call-log, Accessibility or device-admin capability.
- **One-time scoped cleanup.** On an upgrade from a phone that used the reader, Live Guard
  retires only: the reader's own preferences file (`liveguard_notification_reader`), the
  `voice_messages` key, the reader's stale voice diagnostics **only** when their last
  event was the reader's, and the obsolete `reader` setup-reminder id. An idempotent
  completion marker is written only after every step succeeds, so an interrupted cleanup
  is retried on a later start. Nothing private is logged, uploaded or reported.
  **Not** reset: API keys, app language, A/B voice selections, voice/monitor switches
  (including saved OFF), the data plan, charging/security histories, genuine
  permission-review records, or any other app's data. Previously downloaded public
  translation model files may remain unused in private app storage.
- **Kept and unchanged:** Live Guard's own notifications (battery, charging, thermal,
  storage, daily brief, reviewed setup reminders), general speech/voice previews and the
  two selected answer voices, cleanup/health/security/data features, widgets and the
  verified in-app updater. The 2.39 reader-history section below is retained only to
  describe what that published version did; it is not current behaviour.

## Live Guard 2.39 history — reviewed setup, local notification reading and in-app updates (superseded)

The owner authorized build/publication. These features are now in the verified
2.39/code44 release. Cloud tests/build/signature gates passed; this is **not a
physical-device-tested privacy/delivery guarantee**.

- **Reviewed setup:** Android permissions and optional permanent monitoring, voice,
  charging notification and background Wi-Fi scanning require explicit choices.
  Saved OFF preferences are respected. Optional setup reminder notifications are
  silent aggregate counts, OFF by default, at most daily, and omit identities and
  deliberately OFF/skipped features. No new permanent reminder service.
- **Selective notification reading:** only explicitly selected apps/sender-name
  rules. Default sender name only; each app has a separate full-message option.
  Android notification access is broad and must be granted/revoked in Android
  settings. OFF stops reading but does not itself revoke that Android access.
  App-supplied names are not verified contact identities; no Contacts permission.
  Message content/audio is not logged, saved as a history, sent to AI, added to
  reports, or translated online by Live Guard. Candidate names and deduplication
  fingerprints are bounded session memory; explicit package/sender selections
  persist privately and can be cleared. Default unlocked/screen-on-only; OTP
  suppression is heuristic, not a guarantee that every sensitive message is found.
- **Local language processing:** ML Kit local language identification and English/
  Tamil translation. Unknown/unsupported language or absent/failed model means
  sender-only. A model download needs explicit consent, Wi-Fi by default; Google
  receives model-download/connection metadata, not private message text for
  translation. SDK operational metadata follows Google's SDK policy. A consented
  SDK download may complete after leaving the screen. Notification speech selects
  an installed voice reported as non-network; the external TTS engine implements
  it and its own policy applies. Live Guard has no online private-message speech
  fallback. No callback or registration proves that you heard a voice.
- **Last observed unplug:** one local clearable disconnect timestamp/available
  battery level, separate from opt-in charging-session history. Created only by an
  actual observed power-disconnect event, not by opening an unplugged phone.
  Missed events/force-stop/OEM restrictions mean unknown or a last-observed record,
  never an invented actual-latest time/percentage. Phone-clock limitations apply.
- **Verified APK update:** explicit official GitHub download to a private cache
  directory, published digest/size and package/newer-version/certificate checks;
  only that directory can be shared through FileProvider with Android's installer.
  GitHub/CDN receives normal download metadata. No browser download or silent
  installation. Android grants unknown-apps access and controls installation/
  signature verification/Play Protect; no scan or bypass guarantee. Cache may be
  removed by Android/uninstall, or upon failed/superseded downloads.
- **LM Arena:** optional public dataset refresh from official `lmarena/arena-catalog`
  GitHub source, locally exact-matched model ID and dated category/interval. Your
  selected model/API key is not included in the public-source request. GitHub
  receives normal connection metadata. The verified feed is dated December2025,
  not proof of a current October2026 rating or underlying execution model.
- **Online daily reports:** pending source requires separate explicit daily-AI
  consent, default OFF. Allowed phone facts can be sent to the selected provider
  and consume quota. Notification content is never included. Manual device AI
  respects the network-sharing switch, omits Wi-Fi network name, uses one bounded
  batch, and does not save new AI guesses as verified names/identities.

Actual SDK/engine privacy gates, permission/redaction/installation behavior and
background delivery still require physical-phone validation. Cloud compilation/
regression/signature checks do not prove audible speech, OEM survival or a
forced Play Protect scan.
