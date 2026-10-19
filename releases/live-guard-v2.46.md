# Live Guard 2.46

Verified update focused on the optional floating-volume overlay and migration visibility. The old `InternetRadio` release source remains a fallback while `RP-Android-Apps` is checked first.

## Highlights

- The floating volume button is smaller, fades to a low-opacity idle state, can be dragged and snapped to any screen corner, and remembers its chosen corner. Tapping it still opens Android's native media-volume panel; it does not change volume by itself and remains separate from the charging/data pill.
- The updater states its source order and identifies the repository that supplied an available release. Android's per-app install permission and confirmation remain required; Play Protect is not bypassed.
- No new Android permission is required for these overlay changes.

## Installation and safety

Download `CleanSweep-v2.46.apk` and install it through Android. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

**Device testing:** This exact CI-generated APK was not installed on a phone during this release run; no physical-device test is claimed for these release bytes. The release passed the configured CI and permission-delta checks. This is not Play Protect approval or an all-device compatibility claim.

## தமிழ்

# லைவ் கார்டு 2.46

விருப்ப மிதக்கும் ஒலி பொத்தான் மற்றும் புதுப்பிப்பு மூலத் தெளிவுக்கான சரிபார்க்கப்பட்ட புதுப்பிப்பு. `RP-Android-Apps` முதலில் சரிபார்க்கப்படும்; பழைய `InternetRadio` வெளியீட்டு மூலம் fallback-ஆகத் தொடரும்.

- மிதக்கும் ஒலி பொத்தான் சிறியதாகி, பயன்படுத்தாதபோது மங்கும்; திரையின் எந்த மூலைக்கும் இழுத்துச் சென்று பொருத்தலாம், தேர்ந்த மூலையை நினைவில் வைக்கும். தட்டினால் Android-இன் சொந்த மீடியா ஒலி பலகம் திறக்கும்; ஒலியளவை இது தனியாக மாற்றாது. சார்ஜிங்/தரவு pill-இலிருந்து தனியானது.
- புதுப்பிப்பு எந்த வரிசையில் சேமிப்பகங்களைச் சரிபார்க்கிறது, கிடைக்கும் வெளியீடு எந்த சேமிப்பகத்திலிருந்து வந்தது என்பதைக் காட்டும். Android-இன் ஒவ்வொரு பயன்பாட்டிற்குமான நிறுவல் அனுமதியும் உறுதிப்படுத்தலும் தொடரும்; Play Protect தவிர்க்கப்படாது.
- இந்த மேலடுக்கு மாற்றங்களுக்கு புதிய Android அனுமதி தேவையில்லை.

`CleanSweep-v2.46.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.

**சாதனச் சோதனை:** இந்த CI உருவாக்கிய APK இந்த வெளியீட்டு ஓட்டத்தில் போனில் நிறுவப்படவில்லை; இந்தக் குறிப்பிட்ட கோப்புக்கு சாதனச் சோதனை செய்யப்பட்டதாகக் கூறவில்லை. CI மற்றும் அனுமதி-வேறுபாட்டு சோதனைகள் வெற்றியடைந்தன. இது Play Protect ஒப்புதல் அல்லது எல்லாச் சாதனங்களுக்குமான பொருந்துதலை உறுதிப்படுத்துவது அல்ல.
