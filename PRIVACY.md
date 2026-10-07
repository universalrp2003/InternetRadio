# Privacy policy — CleanSweep, Ramesh Radio, Internet Radio, AppForge, PulseEQ

**Last updated: 6 October 2026**
**Developer: Ramesh prathap .R — universalrp2003@gmail.com**

## The short version

These apps are built to work on your phone, not on a server. There is no developer-run server,
no account to create, no advertising, no analytics, and no tracking of any kind. Nothing is
uploaded to the developer, and the developer cannot see anything on your phone.

## What is collected by the developer

**Nothing.** No personal data, no device identifiers, no location, no contacts, no files, no
usage statistics. There is no backend to receive them; the developer has no way to collect
them.

## When an app uses the internet

An app uses the internet only for a feature you start yourself:

| App | Why it can go online | What is sent |
|---|---|---|
| **Ramesh Radio** | To stream the radio station you chose, and to search the public Radio-Browser station directory when you tap search | The station's stream URL, and the search words you type |
| **Internet Radio** | To stream the station you chose | The station's stream URL |
| **CleanSweep — optional AI** | Only after you save your own API key and tap **Analyse**, **Ask** or **Run the daily check now** | The text you are asking about, plus the on-device summary shown in full by “See exactly what was sent” (storage figures, battery and temperature readings, security findings, and app names *only if you leave that switch on*). It goes straight to the provider whose key you pasted — Google Gemini, NVIDIA, OpenRouter, Groq, OpenAI, or a custom endpoint — never to the developer |
| **CleanSweep — speed test** | Only when you tap the speed test | Nothing but the test traffic itself |
| **CleanSweep — public IP / ping** | Only on the Mobile & data screen | A request to the check service (Cloudflare, ipinfo.io, ipapi.co or ipify); no data about you is included |
| **AppForge, PulseEQ** | Never — **PulseEQ has no INTERNET permission at all**, and AppForge works entirely on the phone | — |

Search words, station URLs and AI questions are handled by those third parties under their own
privacy policies, because the request goes from your phone directly to them.

## Permissions, and why each one is needed

| Permission | App | Why |
|---|---|---|
| All files access (`MANAGE_EXTERNAL_STORAGE`) | CleanSweep | To find junk, duplicates, old downloads and empty folders. **Nothing is ever deleted without you selecting it and confirming.** |
| Notifications | CleanSweep | Battery, heat and daily-brief messages. Can be switched off in the app. |
| Battery / charging foreground service | CleanSweep | The charging card in the status bar. Stops when the charger is unplugged. |
| Location | CleanSweep | Only on the Wi-Fi and Mobile screens: Android itself hides the Wi-Fi network details, signal strength and cell-tower list without it. CleanSweep never records or transmits your location. |
| Phone state (`READ_PHONE_STATE`) | CleanSweep | Only to show the real network type (5G/4G), operator and signal on the Mobile screen. |
| Usage access | CleanSweep | Only to show per-app cache sizes and data usage; you grant it in system settings and can revoke it there. |
| Display over other apps | CleanSweep, off by default | The optional little watt reading in the status bar. |
| Media / audio | Ramesh Radio, PulseEQ | To play your own audio files and to run the equalizer. |
| Internet | CleanSweep, Ramesh Radio, Internet Radio | See the table above. |

CleanSweep does **not** use an Accessibility service: it cannot read your screen and cannot tap
inside other apps. Microphone input exists only in the assistant's voice button, which uses
Android's own speech recogniser — no audio is recorded or stored, and there is no `RECORD_AUDIO`
permission. The Wi-Fi network name (SSID) is never shown and never sent anywhere.

## Children

These apps are general-purpose tools, not directed at children, and they collect no data from
anyone.

## Data retention and deletion

The apps keep their settings, your AI key (stored in the app's private storage on your phone),
favourites and the last daily brief on your phone only. Uninstalling the app removes all of it.
If you use the optional AI, deleting the key in AI settings stops all AI requests immediately.
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
