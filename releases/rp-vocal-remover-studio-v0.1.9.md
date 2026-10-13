# RP VOCAL REMOVER STUDIO 0.1.9

Standalone stable direct-download APK; this release is separate from Ramesh Radio and the other apps.

## Highlights

- Tamil-default interface with an in-app English switch.
- Browse, open and play saved WAV/MP3 results, with playback volume control.
- Compact device-status and thermal/RAM readings near the top of the main screen. Unavailable sensor values are labelled rather than guessed.
- The first pass creates a separate instrumental WAV and leaves the original file unchanged. A second vocal-reduction pass starts only when the user chooses it after listening.
- Optional 320 kb/s CBR MP3 export is separate and lossy; the WAV is retained.
- Processing remains on-device. The separation model is downloaded only after the user requests it and its checksum is verified; selected audio is never uploaded.
- Queued long jobs support pause/resume and safe checkpoints. Low-memory pauses can resume after memory recovers; user, thermal and storage pauses remain under user control.
- Adds verified GitHub release checks and APK hand-off to Android. The updater checks the package, version, SHA-256 digest and signing identity before opening Android's installer.

## Installation and compatibility

Download `RP-Vocal-Remover-Studio-v0.1.9.apk` and install it through Android. This build requires Android 10 (API 29) or later for its verified native processing runtime. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent install or Play Protect bypass is used.

**Device testing:** The owner reports that phone testing already performed is sufficient for this publication. This note does not claim that these exact CI-generated release bytes were installed on a phone or that the app is certified for every device.

## தமிழ்

# RP VOCAL REMOVER STUDIO 0.1.9

தனி நிலையான APK வெளியீடு; இது Ramesh Radio அல்லது மற்ற பயன்பாடுகளுடன் இணைக்கப்படவில்லை.

- தமிழ் இயல்புநிலை மொழி; பயன்பாட்டிற்குள் ஆங்கிலத்துக்கு மாறலாம்.
- சேமித்த WAV/MP3 கோப்புகளைப் பார்க்க, திறக்க, கேட்கவும் ஒலியளவைக் கட்டுப்படுத்தவும் முடியும்.
- சாதன நிலை, வெப்பநிலை மற்றும் RAM அளவுகள் பிரதான திரையின் மேல்பகுதியில் சுருக்கமாகக் காட்டப்படும். கிடைக்காத சென்சார் மதிப்புகள் ஊகிக்கப்படாமல் குறிப்பிடப்படும்.
- முதல் செயலாக்கம் தனி கருவியிசை WAV-ஐ உருவாக்கும்; அசல் கோப்பு மாற்றப்படாது. கேட்டுப் பார்த்த பிறகு பயனர் தேர்ந்தெடுத்தால் மட்டுமே விருப்பமான இரண்டாவது குரல்-குறைப்பு செயலாக்கம் தொடங்கும்.
- தனியாக 320 kb/s CBR MP3 ஏற்றுமதி உள்ளது. MP3 தர இழப்புடையது; WAV பாதுகாக்கப்படும்.
- ஒலி செயலாக்கம் சாதனத்திலேயே நடக்கும். பயனர் கேட்டால் மட்டுமே பிரிப்பு மாடல் பதிவிறக்கப்பட்டு அதன் checksum சரிபார்க்கப்படும்; தேர்ந்தெடுத்த பாடல் பதிவேற்றப்படாது.
- நீண்ட வரிசைப் பணிகளுக்கு இடைநிறுத்தம்/தொடர்வு மற்றும் பாதுகாப்புச் சோதனைப் புள்ளிகள் உள்ளன. RAM குறைவு சரியானதும் அந்த இடைநிறுத்தம் தானாகத் தொடரலாம்; பயனர், வெப்பம் மற்றும் சேமிப்பு இடைநிறுத்தங்கள் பயனர் கட்டுப்பாட்டிலேயே இருக்கும்.
- GitHub வெளியீடு, தொகுப்பு, பதிப்பு, SHA-256 மற்றும் கையொப்பம் சரிபார்க்கப்பட்ட பிறகே Android நிறுவி திறக்கும்.

இந்தப் பயன்பாட்டிற்கு Android 10 (API 29) அல்லது அதற்குப் பிறகான பதிப்பு தேவை. `RP-Vocal-Remover-Studio-v0.1.9.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.

**சாதனச் சோதனை:** உரிமையாளர் ஏற்கெனவே செய்த போன் சோதனை இந்த வெளியீட்டிற்கு போதுமானது எனத் தெரிவித்துள்ளார். இந்தக் குறிப்பிட்ட CI கோப்புகள் போனில் நிறுவப்பட்டதாகவோ எல்லா சாதனங்களுக்கும் சான்றளிக்கப்பட்டதாகவோ இக்குறிப்பு கூறவில்லை.
