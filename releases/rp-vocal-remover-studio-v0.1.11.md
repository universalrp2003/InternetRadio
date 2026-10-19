# RP VOCAL REMOVER STUDIO 0.1.11

Standalone migration-visibility update. RP Vocal Remover Studio remains separate from Ramesh Radio and the other apps. `RP-Android-Apps` is checked first, with `InternetRadio` retained as a grace-period fallback.

## Highlights

- The updater identifies the repository that supplied an available release and explains the new-first/old-fallback order.
- Local-only audio processing is unchanged. Selected audio is not uploaded; Android's per-app install permission and confirmation remain required, and Play Protect is not bypassed.
- This release does not change the processing model, first-pass WAV behavior, optional second vocal-reduction pass, or optional lossy MP3 export.

## Installation and compatibility

Download `RP-Vocal-Remover-Studio-v0.1.11.apk` and install it through Android. This build requires Android 10 (API 29) or later for its verified native processing runtime. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

**Device testing:** The owner considers the existing phone testing sufficient for this release. This note does not claim that these exact CI-generated APK bytes were installed on a phone or that the app is certified for every device.

## தமிழ்

# RP VOCAL REMOVER STUDIO 0.1.11

தனி இடமாற்றத் தெளிவுப் புதுப்பிப்பு. RP Vocal Remover Studio, Ramesh Radio மற்றும் பிற பயன்பாடுகளிலிருந்து தனியாகவே இருக்கும். `RP-Android-Apps` முதலில் சரிபார்க்கப்படும்; grace period-இல் `InternetRadio` fallback-ஆகத் தொடரும்.

- கிடைக்கும் வெளியீடு எந்த சேமிப்பகத்திலிருந்து வந்தது மற்றும் புதியது-முதலில் / பழையது-fallback வரிசை ஆகியவற்றைப் புதுப்பிப்பி காட்டும்.
- சாதனத்திலேயே செய்யும் ஒலி செயலாக்கம் மாறவில்லை. தேர்ந்தெடுத்த ஒலி பதிவேற்றப்படாது; Android-இன் ஒவ்வொரு ஆப்பிற்குமான நிறுவல் அனுமதியும் உறுதிப்படுத்தலும் தொடரும்; Play Protect தவிர்க்கப்படாது.
- processing model, முதல்-pass WAV, விருப்பமான இரண்டாம் குரல்-குறைப்பு அல்லது விருப்பமான தர இழப்புள்ள MP3 ஏற்றுமதி மாற்றப்படவில்லை.

இந்தப் பயன்பாட்டிற்கு Android 10 (API 29) அல்லது அதற்குப் பிந்தைய பதிப்பு தேவை. `RP-Vocal-Remover-Studio-v0.1.11.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.

**சாதனச் சோதனை:** உரிமையாளர் ஏற்கெனவே செய்த போன் சோதனை இந்த வெளியீட்டிற்கு போதுமானது எனக் கருதுகிறார். இந்த CI உருவாக்கிய குறிப்பிட்ட APK கோப்புகள் போனில் நிறுவப்பட்டதாகவோ எல்லாச் சாதனங்களுக்கும் சான்றளிக்கப்பட்டதாகவோ இந்தக் குறிப்பு கூறவில்லை.
