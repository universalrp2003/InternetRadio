# Oor Arivippu 1.0.1 — Stable

**Release tag:** `oor-arivippu-v1.0.1`  
**Package:** `com.oorarivippu.voiceannouncer`  
**Version code:** 3  
**APK:** `Oor-Arivippu-v1.0.1.apk`

## English

An update to Oor Arivippu 1.0.0 with cleaner place names and Android voice selection.

- Plus Codes (for example `9X5H+3HF`), coordinate-only labels and number-only address fragments are no longer shown or spoken as place names. Names are compared after this cleanup, so a Plus-Code-only change does not repeat an announcement, while a real named change, such as moving from a hospital to a bus stand in the same town, is still announced. Long multi-stop road descriptions no longer replace a named place.
- Separate Tamil and English voice choices: pick one of up to four voices offered by the phone's Android Text-to-Speech engine for each language, or keep the Android system default. Each voice is labelled **On-device** or **Requires Internet**. An Internet voice may send the spoken text to its TTS provider; see the privacy policy.
- Offline-status reminders still use only an on-device voice or a short local beep; Internet voices are never used for them. Place and destination announcements stay paused while there is no internet.
- No new Android permissions. The package and RP Ideas signing certificate are the same as 1.0.0, so a stable 1.0.0 installation can update to this version through the verified in-app updater or a manual install. Android still shows its own install confirmation and Play Protect decision; the app never installs silently.

**Moving from a test build:** Oor Arivippu test APKs are debug-signed. Android does not allow this stable, RP Ideas-signed APK to update a debug-signed test installation. Uninstall the test APK first; uninstalling removes its local settings and announcement history.

**Testing:** the owner installed the debug test APK from GitHub Actions run 38776902130, built from the same app source before the version number was raised to 1.0.1, and reported that it opened and worked: Play Protect showed “This app looks safe”, the Tamil/English voice picker listed the phone's on-device and Internet voices, and the current place was announced. That test build reported version 1.0.0. After publication, the owner also installed the debug APK from Oor test run 38826564762, built from the exact 1.0.1 source; it reported 1.0.1 (3), Play Protect again showed “This app looks safe”, the updater reported no newer release, and the owner reported that it works. Both test builds were debug-signed, so their bytes differ from this stable APK; the exact bytes of this release were not device-tested. The published APK is 12,195,397 bytes with SHA-256 `ec10c7369807748c9a5608f1a5c6a37eff5f8e1f7433cc16e984c75d8958457d`.

## தமிழ்

ஊர் அறிவிப்பு 1.0.0-க்கான புதுப்பிப்பு: தெளிவான இடப் பெயர்களும் Android குரல் தேர்வும்.

- Plus Code-கள் (எ.கா. `9X5H+3HF`), அட்சரேகை/தீர்க்கரேகை மட்டும் உள்ள லேபிள்கள், எண்கள் மட்டும் உள்ள முகவரிப் பகுதிகள் இனி இடப் பெயராகக் காட்டப்படவோ சொல்லப்படவோ மாட்டாது. இந்தச் சுத்திகரிப்புக்குப் பிறகே பெயர்கள் ஒப்பிடப்படுவதால், Plus Code மட்டும் மாறினால் அறிவிப்பு மீண்டும் வராது; ஆனால் ஒரே ஊரில் மருத்துவமனையிலிருந்து பேருந்து நிலையத்துக்கு நகர்வது போன்ற உண்மையான பெயர் மாற்றம் தொடர்ந்து அறிவிக்கப்படும். பல ஊர்களை இணைக்கும் நீண்ட சாலை விவரங்கள் இனி இடப் பெயருக்குப் பதிலாக வராது.
- தமிழுக்கும் ஆங்கிலத்துக்கும் தனித்தனிக் குரல் தேர்வு: ஒவ்வொரு மொழிக்கும் தொலைபேசியின் Android Text-to-Speech வழங்கும் அதிகபட்சம் நான்கு குரல்களில் ஒன்றைத் தேர்வு செய்யலாம் அல்லது Android இயல்புக் குரலையே வைத்துக்கொள்ளலாம். ஒவ்வொரு குரலும் **சாதனத்திலேயே செயல்படும்** அல்லது **இணையம் தேவை** என்று குறிக்கப்படும். இணையக் குரலைத் தேர்ந்தெடுத்தால், பேசப்படும் உரை அதன் TTS வழங்குநருக்கு அனுப்பப்படலாம்; தனியுரிமைக் கொள்கையைப் பார்க்கவும்.
- இணையமில்லா நிலை நினைவூட்டல்கள் சாதனத்திலேயே செயல்படும் குரல் அல்லது குறுகிய உள்ளூர் பீப் ஒலியை மட்டுமே பயன்படுத்தும்; அவற்றுக்கு இணையக் குரல் ஒருபோதும் பயன்படுத்தப்படாது. இணையம் இல்லாதபோது இடம் மற்றும் இலக்கு அறிவிப்புகள் நிறுத்தப்பட்டே இருக்கும்.
- புதிய Android அனுமதிகள் இல்லை. package-உம் RP Ideas கையொப்பச் சான்றிதழும் 1.0.0-இல் உள்ளவையே; எனவே நிலையான 1.0.0 நிறுவலைச் செயலிக்குள் உள்ள சரிபார்க்கப்பட்ட புதுப்பிப்பு வசதி மூலமாகவோ நேரடி நிறுவல் மூலமாகவோ இந்தப் பதிப்புக்குப் புதுப்பிக்கலாம். Android தனது நிறுவல் உறுதிப்படுத்தலையும் Play Protect முடிவையும் காட்டும்; செயலி அமைதியாகத் தானே நிறுவாது.

**சோதனைப் பதிப்பிலிருந்து மாறுதல்:** ஊர் அறிவிப்பு சோதனை APK-கள் debug கையொப்பம் கொண்டவை. debug கையொப்பமுள்ள சோதனை நிறுவலை இந்த நிலையான RP Ideas கையொப்ப APK-ஆல் புதுப்பிக்க Android அனுமதிக்காது. முதலில் சோதனை APK-ஐ நீக்கவும்; நீக்கும்போது அதன் உள்ளூர் அமைப்புகளும் அறிவிப்பு வரலாறும் அழியும்.

**சோதனை:** GitHub Actions run 38776902130-இல் உருவான debug சோதனை APK-ஐ உரிமையாளர் நிறுவி, அது திறந்து இயங்குவதாகத் தெரிவித்தார்: Play Protect “This app looks safe” என்று காட்டியது, தமிழ்/ஆங்கிலக் குரல் தேர்வில் தொலைபேசியின் சாதனக் குரல்களும் இணையக் குரல்களும் பட்டியலிடப்பட்டன, தற்போதைய இடம் அறிவிக்கப்பட்டது. அந்த APK, பதிப்பு எண் 1.0.1 ஆக உயர்த்தப்படுவதற்கு முன் அதே செயலி மூலக் குறியீட்டிலிருந்து உருவானது; அது 1.0.0 என்று காட்டியது. வெளியீட்டுக்குப் பிறகு, சரியான 1.0.1 மூலக் குறியீட்டிலிருந்து Oor test run 38826564762-இல் உருவான debug APK-ஐயும் உரிமையாளர் நிறுவினார்; அது 1.0.1 (3) என்று காட்டியது, Play Protect மீண்டும் “This app looks safe” என்று காட்டியது, புதிய வெளியீடு இல்லை என்று புதுப்பிப்பு வசதி தெரிவித்தது, அது இயங்குவதாக உரிமையாளர் தெரிவித்தார். இரண்டு சோதனை APK-களும் debug கையொப்பம் கொண்டவை; எனவே அவற்றின் bytes இந்த நிலையான APK-இலிருந்து வேறுபடும்; இந்த வெளியீட்டின் அதே bytes சாதனத்தில் சோதிக்கப்படவில்லை. வெளியிடப்பட்ட APK அளவு 12,195,397 bytes; SHA-256 `ec10c7369807748c9a5608f1a5c6a37eff5f8e1f7433cc16e984c75d8958457d`.
