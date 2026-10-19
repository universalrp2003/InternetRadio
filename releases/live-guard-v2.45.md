# Live Guard 2.45

Verified bridge APK for Live Guard, formerly CleanSweep. It keeps the existing InternetRadio release source as a fallback while adding RP-Android-Apps as the preferred source for verified updates.

## Highlights

- Duplicate scanning treats partial hashes as candidate filters; full file contents must match before files are classified as duplicates.
- Charging values stay unavailable when the phone cannot provide a valid measurement. Time-to-full is shown only after a sufficiently recent, process-local observed charge rise; observations are not persisted or uploaded.
- AI settings report whether a provider/key is configured without revealing the key. The retired model-rating card and data are not included.
- Connection counts are best-effort current observations, separate from historical usage. Per-app controls open that app's Android settings; the app does not claim it can force-stop other apps.
- The floating volume control is optional and separate from the charging/data pill.
- Update discovery checks RP-Android-Apps first and InternetRadio second. APK digest, package, version, canonical HTTPS release URL and signer are checked before Android's installer is opened.

## Installation and safety

Download `CleanSweep-v2.45.apk` and install it through Android. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

**Device testing:** This exact CI-generated APK was not installed on a phone during this release run; no physical-device test is claimed for these release bytes. The release passed the configured CI and permission-delta checks. This is not Play Protect approval or an all-device compatibility claim.

## தமிழ்

# லைவ் கார்டு 2.45

முன்பு CleanSweep என அழைக்கப்பட்ட Live Guard-இன் சரிபார்க்கப்பட்ட bridge APK. புதிய புதுப்பிப்புகளுக்கு RP-Android-Apps-ஐ முதலில் பார்க்கும்; பழைய InternetRadio வெளியீட்டு முகவரியையும் தற்கால fallback ஆக வைத்திருக்கும்.

- பகுதி hash-கள் நகல் கோப்புகளைத் தேர்ந்தெடுக்க மட்டும் பயன்படும்; நகல் என வகைப்படுத்துவதற்கு முழுக் கோப்பு உள்ளடக்கமும் பொருந்த வேண்டும்.
- சரியான அளவீடு கிடைக்காவிட்டால் சார்ஜிங் மதிப்பு காட்டப்படாது. போதுமான சமீபத்திய உள்ளூர் சார்ஜ் உயர்வு கவனிக்கப்பட்ட பிறகே முழுச் சார்ஜ் நேரம் காட்டப்படும்; அந்தக் கவனிப்புகள் சேமிக்கப்படவோ பதிவேற்றப்படவோ மாட்டா.
- AI அமைப்புகள் provider/key உள்ளதா என்பதைக் காட்டும்; ரகசிய key-ஐ காட்டாது. நீக்கப்பட்ட model-rating அட்டை மற்றும் தரவு சேர்க்கப்படவில்லை.
- தற்போதைய இணைப்புகளின் எண்ணிக்கை சிறந்த முயற்சி மதிப்பீடு; அது வரலாற்று பயன்பாட்டு கணக்கிலிருந்து வேறு. ஒவ்வொரு பயன்பாட்டிற்கும் Android அமைப்புகள் திறக்கப்படும்; மற்ற பயன்பாடுகளை வலுக்கட்டாயமாக நிறுத்துவதாகக் கூறவில்லை.
- மிதக்கும் ஒலியளவு பொத்தான் விருப்பமானது; சார்ஜிங்/தரவு pill-இலிருந்து தனியானது.
- புதுப்பிப்பு முதலில் RP-Android-Apps-ஐயும் பின்னர் InternetRadio-வையும் பார்க்கும். Android நிறுவியைத் திறப்பதற்கு முன் digest, package, version, canonical HTTPS release URL மற்றும் signer சரிபார்க்கப்படும்.

`CleanSweep-v2.45.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.

**சாதனச் சோதனை:** இந்த CI உருவாக்கிய APK இந்த வெளியீட்டு ஓட்டத்தில் போனில் நிறுவப்படவில்லை; இந்த குறிப்பிட்ட கோப்புக்கு சாதனச் சோதனை செய்யப்பட்டதாகக் கூறவில்லை. CI மற்றும் அனுமதி-வேறுபாட்டு சோதனைகள் வெற்றியடைந்தன. இது Play Protect ஒப்புதல் அல்லது எல்லாச் சாதனங்களுக்குமான பொருந்துதலை உறுதிப்படுத்துவது அல்ல.
