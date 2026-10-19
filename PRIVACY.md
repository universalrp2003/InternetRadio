# Privacy policy — RP Ideas Android apps

**Last updated: 16 October 2026** · **Developer: Ramesh prathap .R — universalrp2003@gmail.com**

This policy covers five actively maintained Android apps and their current stable releases: **Live Guard (CleanSweep) 2.46**, **Ramesh Radio 1.11**, **PulseEQ 1.5**, **RP Vocal Remover Studio 0.1.11**, and **Oor Arivippu 1.0.2**. Older app versions and unreleased builds may behave differently.

## The short version

The developer does not operate an app backend, account system, advertising network, analytics or crash-reporting service. The developer does not receive or see data on your phone. Each app stores its own settings and local content on the device. When you choose an online feature, your phone connects directly to the relevant provider; that provider receives the request and ordinary connection data such as your IP address under its own privacy policy.

**RP Vocal Remover Studio processes selected audio on-device. It has no selected-audio upload path and does not use an external audio-processing service.** Its model may be downloaded from GitHub and verified before use.

## App-by-app data use

### Live Guard (CleanSweep)

- **Local use:** The app reads device, battery, storage, network and permission information to display its phone-care and review features. Settings, favourites, summaries and any enabled histories are stored in app-private storage. AI provider keys and the optional VirusTotal key are encrypted with Android Keystore before being saved; provider keys can be explicitly forgotten in AI settings, and the VirusTotal key can be removed in the malware-check settings. The optional security, charging and mobile-data histories are user-controlled and remain local. All five apps set Android `allowBackup=false`. The four legacy modules also declare explicit rules excluding app-private files, databases and preferences from cloud backup and device transfer where Android honors those rules; OEM transfer behavior may differ, so review the device's system backup settings as well. Clearing in-app history, clearing app data or uninstalling removes the app-private records; Android controls the lifecycle of the Keystore key. Files exported or changed outside the app remain subject to the chosen destination and Android's storage rules.
- **File cleanup:** All-files access is used to find and review files. The app does not delete files without the user's selection and confirmation.
- **Optional AI and assistant:** When you request an AI analysis, chat answer, or an enabled daily brief, the selected provider receives the question and the phone-care details included in that request. Depending on your sharing choices, this can include device readings and findings, app names or package/service identifiers, available scan results, and network observations. The app provides a “see exactly what was sent” view for the analysis request. Do not include secrets in prompts. Requests go from the phone to the provider you chose; the developer does not proxy them. Provider retention and use are governed by that provider's policy.
- **Other online tools:** User-started malware-hash lookups send APK SHA-256 hashes, not APK files, to MalwareBazaar and optionally VirusTotal when configured. Public-IP, ping and network-quality features contact the selected public endpoints; a speed test sends test traffic only. News refreshes may request public RSS/Atom feeds, including Google News. These hosts receive their normal request and connection metadata. If you use online text-to-speech, the selected TTS provider may receive the text being spoken. Voice input uses Android's speech-recognition flow; Android's recognizer may process audio under its own provider settings and privacy policy. Live Guard does not save recordings or upload microphone audio to the developer.
- **Permissions:** Depending on the feature and Android version, Live Guard may request all-files access, notifications, location (Android requires it to expose certain Wi-Fi/cell information), phone-state access, usage access, or display-over-other-apps access. Permissions are used for the feature described in the app and can be reviewed or revoked in Android Settings. Live Guard does not use an Accessibility service or notification-listener service and does not request SMS, contacts, call-log or device-admin access.

### Ramesh Radio

- Station audio streams directly from the station selected by the user. HTTPS is preferred when a station offers it; legacy HTTP streams remain available for compatibility. HTTP audio is not encrypted in transit, so other parties on the network may be able to observe or alter the stream. The station and hosting provider receive the connection and normal network metadata.
- The bundled station list is available offline. A public Radio-Browser directory search is made only when the user starts a search; the directory receives the search terms and normal connection metadata.
- Local songs are selected and played on-device. The app does not upload local audio. Audio/media permissions or Android's file picker are used to choose local files.

### PulseEQ

- Local audio playback and equalization are processed on-device. Selected audio is not uploaded by the app.
- The optional GitHub release check contacts GitHub and sends ordinary request metadata; it does not send audio.
- Whether effects apply outside PulseEQ depends on Android audio sessions and the device/ROM. System-wide processing is not guaranteed.

### Oor Arivippu (Place Announcer)

- **Location and tracking:** When the user starts a trip, the app reads location from Android's location services. Foreground location is required; background location is a separate optional Android permission for screen-off tracking. The app processes current readings on-device for place announcements and distance checks against the selected destination. It does not send location to a developer-operated backend or receive it at the developer.
- **Geocoder lookups:** The app passes coordinates to Android's Geocoder to resolve place names and passes a user-entered place name to the Geocoder when setting a destination. Depending on the Android/OEM Geocoder implementation, a system or provider service may perform the lookup online and receive relevant coordinates or search text under that provider's privacy policy. This behavior is outside the app developer's control and can vary by device. If validated internet is unavailable, Oor Arivippu pauses new place/district/highway and destination announcements; GPS tracking can continue.
- **Voice output:** Spoken place and destination alerts use the Android Text-to-Speech engine selected on the phone. The user can choose from up to four Tamil or English voices exposed by that engine, including voices Android marks as network-dependent. If a network voice is selected, its TTS provider may receive the text being spoken; handling is controlled by that provider. The app does not operate a speech server.
- **Offline reminders:** The user may choose Off, 5, 10, 15 or 30 minutes (15-minute default) for reminders while tracking without internet. The reminder says that internet is unavailable and that place/destination speech is paused. It uses a locally available on-device TTS voice or a short local beep; the app does not use an online voice service for the reminder.
- **Local data and permissions:** Preferences, the selected destination name/coordinates and a bounded announcement history are stored in app-private storage. The app requests foreground location, notifications as required by Android, internet/network state, and optionally background location. Its updater may hand a verified APK to Android's installer; Android controls the per-app install permission and confirmation. Oor Arivippu does not request microphone, contacts, phone or call-log permissions.

### RP Vocal Remover Studio

- Selected songs are processed on-device. The original is left unchanged; the first pass creates an instrumental WAV. A second vocal-reduction pass is optional and user-started. A separate 320 kb/s CBR MP3 export is optional; MP3 is lossy and the WAV is retained.
- The model is downloaded from the upstream GitHub release and checked against its pinned checksum. GitHub receives normal download request metadata. The model download is not an audio upload.
- The app does not send the selected audio to the developer, GitHub, an AI provider or an audio-processing service. Output files are written to the location selected by the user and remain subject to Android storage and backup settings.

## Release checks and APK installation

The apps may contact GitHub to check for stable updates or download a user-requested update/model. These requests expose normal GitHub connection metadata, including the device's IP address. No selected audio is included. Before opening Android's installer, an app verifies the release asset and package identity. Installation is not silent: Android may require the per-app **Install unknown apps** permission and always shows its own confirmation. The apps do not bypass Play Protect or change this Android permission for you.

## Data retention and deletion

- Settings, credentials and locally generated records stay within each app's storage unless you explicitly export or share them. Credential storage is local to the app; users should still protect their device and avoid sharing screenshots or exports that contain secrets.
- Optional Live Guard histories remain until cleared, app data is removed, or the app is uninstalled. Turning a history off stops new collection but does not necessarily erase existing records; use the relevant Clear control to delete them.
- Radio favourites, Oor Arivippu settings, its selected destination and its announcement history remain local. Oor history can be cleared in the app; clearing app data or uninstalling also removes its private preferences. Radio audio files and vocal-removal outputs are in locations chosen through Android and are not removed by uninstalling the app unless Android or the user removes them.
- Online providers may retain requests under their own terms and retention policies. To ask a provider about its records, contact that provider; the app developer has no copy to delete.

## Children

These general-purpose utilities are not designed specifically for children. The apps do not knowingly collect personal data for the developer.

## Changes and contact

Changes to this policy will be published in this file with its date updated. Questions can be sent to **Ramesh prathap .R** at **universalrp2003@gmail.com**.

Source code and issue tracker: <https://github.com/universalrp2003/InternetRadio>
