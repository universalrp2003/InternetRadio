# Privacy policy — CleanSweep, Ramesh Radio, Internet Radio, AppForge, PulseEQ

**Last updated: 10 October 2026**
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
| **CleanSweep — assistant** | Online Ask or optional live web lookup | The question, phone-care context and web-search query. Do not type secrets. General chat is separate from the security report; security-report findings are not sent to web search |
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
inside other apps. Microphone input exists only in the assistant's voice button, which uses
Android's own speech recogniser — no audio is recorded or stored, and there is no `RECORD_AUDIO`
permission. Wi-Fi details can be displayed after the relevant permission is granted.
In AI reports, the network-sharing switch controls sending the available SSID, local
addresses/DNS, device-scan details and network/data observations. Turning it off
omits those fields. The security AI report also honours the app-name switch,
including labels embedded in finding/malware descriptions. It does not send file
contents, file paths, messages, contacts, credentials or GPS coordinates.

## Children

These apps are general-purpose tools, not directed at children, and they collect no data from
anyone.

## Data retention and deletion

The apps keep their settings, your AI key (stored in the app's private storage on your phone),
favourites and the last daily brief on your phone only. Uninstalling the app removes all of it.
Deleting a provider key stops authenticated requests to that provider, not the Free
lane or an already configured daily brief. Turn off online assistance/AI daily use
if you do not want those requests.
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
