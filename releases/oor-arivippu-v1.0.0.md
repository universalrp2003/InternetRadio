# Oor Arivippu 1.0.0 — Stable

**Release tag:** `oor-arivippu-v1.0.0`  
**Package:** `com.oorarivippu.voiceannouncer`  
**Version code:** 2  
**APK:** `Oor-Arivippu-v1.0.0.apk`

## English

First stable release of Oor Arivippu, a Tamil/English GPS place announcer.

- Adds an About section with contributor credits, verified updater controls, repository links, and current RP Ideas app release links.
- Adds selectable offline-status reminders: Off, 5, 10, 15, or 30 minutes (15-minute default). Place and destination announcements remain paused without internet. Offline reminders use an installed on-device TTS voice when available and otherwise a short local beep; network voices are not used for these reminders.
- Adds verified GitHub Releases update discovery and APK checks for the official asset URL, size, SHA-256, package/version, and installed signing certificate. Android continues to own the per-app install permission, installer confirmation, and Play Protect decision; the app does not install silently or bypass protection.

**Moving from a test build:** earlier Oor Arivippu test APKs were debug-signed. Android does not permit an APK signed by the stable RP Ideas key to update a test APK signed with a different certificate. If you installed a test APK, uninstall it before installing this stable release. Uninstalling removes its local preferences and announcement history.

The owner reports successful testing on two devices and that the stable app is working. Owner-provided screenshots show the `Oor-Arivippu-v1.0.0.apk` install flow, package `com.oorarivippu.voiceannouncer` version 1.0.0 (2), Play Protect's “This app looks safe” screen, and the running app's About/update screen reporting version 1.0.0 with no newer release. This supports owner-reported device validation of the release version, but the screenshots do not show an APK SHA-256 and do not link the installed bytes to the published asset. The published artifact is 12,179,013 bytes with SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`.

## தமிழ்

தமிழ்/ஆங்கில GPS ஊர் அறிவிப்பானின் முதல் நிலையான வெளியீடு.

- பங்களிப்பாளர் விவரங்கள், சரிபார்க்கப்பட்ட புதுப்பிப்பு வசதி, களஞ்சிய இணைப்புகள் மற்றும் RP Ideas செயலிகளின் வெளியீட்டு இணைப்புகளுடன் About பகுதி சேர்க்கப்பட்டுள்ளது.
- இணையம் இல்லாத நிலைக்கு நினைவூட்டல் இடைவெளி தேர்வு: முடக்கு, 5, 10, 15 அல்லது 30 நிமிடங்கள் (இயல்புநிலை 15 நிமிடங்கள்). இணையம் இல்லாதபோது இடம் மற்றும் இலக்கு அறிவிப்புகள் நிறுத்தப்படும். சாதனத்தில் இணையமின்றி இயங்கும் TTS குரல் இருந்தால் அதைப் பயன்படுத்தும்; இல்லையெனில் குறுகிய உள்ளூர் பீப் ஒலி வரும். இந்த நினைவூட்டலுக்கு இணையக் குரல் பயன்படுத்தப்படாது.
- அதிகாரப்பூர்வ GitHub Release URL, APK அளவு, SHA-256, package/version மற்றும் நிறுவிய கையொப்பச் சான்றிதழைச் சரிபார்க்கும் புதுப்பிப்பு வசதி சேர்க்கப்பட்டுள்ளது. ஒவ்வொரு நிறுவலுக்கும் Android அனுமதி மற்றும் உறுதிப்படுத்தல் தேவை; Play Protect தவிர்க்கப்படாது.

**சோதனைப் பதிப்பிலிருந்து மாறுதல்:** முந்தைய Oor Arivippu சோதனை APK-கள் debug கையொப்பத்துடன் இருந்தன. வேறு கையொப்பமுள்ள test APK-ஐ நிலையான RP Ideas கையொப்பம் கொண்ட APK-ஆல் Android நேரடியாகப் புதுப்பிக்க அனுமதிக்காது. சோதனை APK நிறுவியிருந்தால், நிலையான பதிப்பை நிறுவுவதற்கு முன் அதை நீக்கவும். நீக்கும்போது உள்ளூர் அமைப்புகளும் அறிவிப்பு வரலாறும் அழியும்.

நிலையான செயலி இயங்குவதாகவும், இரண்டு சாதனங்களில் சோதனை வெற்றியடைந்ததாகவும் உரிமையாளர் தெரிவித்துள்ளார். உரிமையாளர் வழங்கிய படங்களில் `Oor-Arivippu-v1.0.0.apk` நிறுவல் நடைமுறை, `com.oorarivippu.voiceannouncer` package-இன் 1.0.0 (2) பதிப்பு, Play Protect-இன் “This app looks safe” திரை, மேலும் செயலியின் About/புதுப்பிப்பு திரையில் 1.0.0 மற்றும் புதிய புதுப்பிப்பு இல்லை என்ற நிலை காணப்படுகிறது. இது பதிப்பு-அடிப்படையிலான உரிமையாளர் தெரிவித்த சாதனச் சரிபார்ப்பை ஆதரிக்கிறது; ஆனால் படங்களில் APK SHA-256 இல்லை, எனவே நிறுவிய bytes வெளியிடப்பட்ட asset-உடன் hash மூலம் பொருத்தப்படவில்லை. வெளியிடப்பட்ட APK அளவு 12,179,013 bytes; SHA-256 `b438e0dbe76c0de975a4ce35bf126b8825cd5af0976fe3db93a11ae8b6f6794d`.
