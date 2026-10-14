# Ramesh Radio 1.11

A verified radio update for the volume-boost path, vote-ranked station lists and migration visibility. `RP-Android-Apps` is checked first; the `InternetRadio` release source remains a fallback during the grace period.

## Highlights

- Fixed the 0–10 dB volume boost on Android 9+ by applying it to the playback session's DynamicsProcessing input gain. Older/fallback audio engines use LoudnessEnhancer. The boost can operate independently of the EQ enable switch; Android's device-volume ceiling still applies, and excessive boost can distort.
- The mixed all-station list keeps health-verified streams ahead of unchecked entries, prioritizes Tamil within each health group, and uses Radio Browser community votes for popularity. Filtered Tamil/category lists likewise keep verified streams ahead and use votes within each health group. Counts are labelled as votes, not listener counts.
- The updater identifies the repository that supplied an available release and explains the new-first/old-fallback order. Android's per-app install permission and confirmation remain required; Play Protect is not bypassed.

## Installation and safety

Download `Ramesh-Radio-v1.11.apk` and install it through Android. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

## தமிழ்

# ரமேஷ் ரேடியோ 1.11

ஒலியளவு Boost, வாக்கு வரிசை நிலையங்கள் மற்றும் புதுப்பிப்பு மூலத் தெளிவுக்கான சரிபார்க்கப்பட்ட புதுப்பிப்பு. `RP-Android-Apps` முதலில் சரிபார்க்கப்படும்; grace period-இல் `InternetRadio` மாற்று மூலமாகத் தொடரும்.

- Android 9+ இல் ஒலியளவு Boost-ஐ playback session-ன் DynamicsProcessing input gain-க்கு இணைத்து 0–10 dB செயல்படாத சிக்கல் சரிசெய்யப்பட்டது. பழைய/மாற்று audio engine-கள் LoudnessEnhancer-ஐப் பயன்படுத்தும். EQ switch அணைந்திருந்தாலும் Boost இயங்கலாம்; Android சாதன ஒலி வரம்பு மாறாது, மிகை Boost ஒலியைச் சிதைக்கலாம்.
- கலந்த அனைத்து நிலையப் பட்டியலில் சரிபார்க்கப்பட்ட ஒலிபரப்புகள் சோதிக்கப்படாதவற்றுக்கு முன் வரும்; ஒவ்வொரு நிலைக் குழுவிலும் தமிழ் நிலையங்களுக்கு முன்னுரிமை, பின்னர் Radio Browser சமூக வாக்குகள் வரிசைப்படுத்தும். தனிப்பட்ட தமிழ்/பிரிவு பட்டியல்களிலும் சரிபார்ப்பு முதலில், ஒவ்வொரு நிலைக் குழுவிலும் வாக்குகள் அடுத்து. எண்ணிக்கை வாக்குகள்; கேட்போர் எண்ணிக்கை அல்ல.
- கிடைக்கும் வெளியீட்டை வழங்கிய சேமிப்பகத்தையும், புதிய சேமிப்பகம்-முதலில் / பழைய fallback வரிசையையும் புதுப்பிப்பி காட்டும். Android-இன் தனிப்பட்ட நிறுவல் அனுமதியும் உறுதிப்படுத்தலும் தொடரும்; Play Protect தவிர்க்கப்படாது.

`Ramesh-Radio-v1.11.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.
