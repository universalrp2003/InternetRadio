# RP VOCAL REMOVER STUDIO 0.1.10

Standalone verified bridge APK. It remains separate from Ramesh Radio and the other apps, and keeps selected audio on-device.

## Highlights

- Recovery handling preserves completed private chunks and provides a private-copy fallback when source access is lost.
- Canceled WAV exports clean up incomplete output; pause/resume and safe checkpoints remain available for long jobs.
- The first pass creates a separate instrumental WAV and leaves the original unchanged. A second vocal-reduction pass starts only when the user chooses it after listening. Optional 320 kb/s CBR MP3 export is lossy; the WAV is retained.
- Processing remains on-device. The model is downloaded only after the user requests it and its checksum is verified; selected audio is never uploaded.
- Update discovery checks RP-Android-Apps first and InternetRadio second. APK digest, package, version, canonical HTTPS release URL and signer are checked before Android's installer is opened.

## Installation and compatibility

Download `RP-Vocal-Remover-Studio-v0.1.10.apk` and install it through Android. This build requires Android 10 (API 29) or later for its verified native processing runtime. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

**Device testing:** The owner reports that phone testing already performed is sufficient for this publication. This note does not claim that these exact CI-generated APK bytes were installed on a phone or that the app is certified for every device.

## தமிழ்

# RP VOCAL REMOVER STUDIO 0.1.10

தனி சரிபார்க்கப்பட்ட bridge APK. இது Ramesh Radio மற்றும் மற்ற பயன்பாடுகளிலிருந்து தனியாகவே இருக்கும்; தேர்ந்தெடுத்த ஒலி சாதனத்திலேயே செயலாக்கப்படும்.

- மீட்பு வசதி முடிந்த தனிப்பட்ட chunk-களைப் பாதுகாக்கும்; மூலக் கோப்பின் அணுகல் இழந்தால் தனிப்பட்ட நகல் வழியை வழங்கும்.
- ரத்து செய்யப்பட்ட WAV ஏற்றுமதியில் முழுமையற்ற வெளியீடு சுத்தம் செய்யப்படும்; நீண்ட பணிகளுக்கான இடைநிறுத்தம்/தொடர்வு மற்றும் பாதுகாப்புச் சோதனைப் புள்ளிகள் தொடர்கின்றன.
- முதல் செயலாக்கம் தனி கருவியிசை WAV உருவாக்கும்; அசல் கோப்பு மாற்றப்படாது. கேட்டுப் பார்த்த பின் பயனர் தேர்ந்தெடுத்தால் மட்டுமே இரண்டாம் குரல்-குறைப்பு தொடங்கும். விருப்பமான 320 kb/s CBR MP3 தர இழப்புடையது; WAV பாதுகாக்கப்படும்.
- ஒலி செயலாக்கம் சாதனத்திலேயே நடக்கும். பயனர் கேட்ட பிறகே model பதிவிறக்கப்பட்டு checksum சரிபார்க்கப்படும்; தேர்ந்தெடுத்த ஒலி பதிவேற்றப்படாது.
- புதுப்பிப்பு முதலில் RP-Android-Apps-ஐயும் பின்னர் InternetRadio-வையும் பார்க்கும். Android நிறுவியைத் திறப்பதற்கு முன் digest, package, version, canonical HTTPS release URL மற்றும் signer சரிபார்க்கப்படும்.

இந்தப் பயன்பாட்டிற்கு Android 10 (API 29) அல்லது அதற்குப் பிந்தைய பதிப்பு தேவை. `RP-Vocal-Remover-Studio-v0.1.10.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.

**சாதனச் சோதனை:** உரிமையாளர் ஏற்கெனவே செய்த போன் சோதனை இந்த வெளியீட்டிற்கு போதுமானது எனத் தெரிவித்துள்ளார். இந்த CI உருவாக்கிய குறிப்பிட்ட APK கோப்புகள் போனில் நிறுவப்பட்டதாகவோ எல்லாச் சாதனங்களுக்கும் சான்றளிக்கப்பட்டதாகவோ இந்தக் குறிப்பு கூறவில்லை.
