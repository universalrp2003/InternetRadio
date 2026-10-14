# PulseEQ 1.4

Verified bridge APK for PulseEQ. It keeps the existing InternetRadio release source as a fallback while adding RP-Android-Apps as the preferred source for verified updates.

## Highlights

- DSP control updates are applied safely at audio block boundaries, avoiding concurrent state changes during processing.
- The spectrum analyser resets when the sample rate changes, preventing stale analyser state from carrying across audio formats.
- Update discovery checks RP-Android-Apps first and InternetRadio second. APK digest, package, version, canonical HTTPS release URL and signer are checked before Android's installer is opened.
- Audio processing remains on-device.

## Installation and safety

Download `PulseEQ-v1.4.apk` and install it through Android. If Android requests it, enable **Allow from this source** for the browser or file manager you used, then confirm the installation in Android's installer. No silent installation or Play Protect bypass is used.

## தமிழ்

# PulseEQ 1.4

PulseEQ-க்கான சரிபார்க்கப்பட்ட bridge APK. புதிய புதுப்பிப்புகளுக்கு RP-Android-Apps-ஐ முதலில் பார்க்கும்; InternetRadio-வை தற்கால fallback ஆக வைத்திருக்கும்.

- ஒலி செயலாக்கத்தின் போது ஒரே நேரத்தில் நிலை மாறாமல் இருக்க, DSP கட்டுப்பாட்டு மாற்றங்கள் பாதுகாப்பாக audio block எல்லைகளில் பயன்படுத்தப்படும்.
- sample rate மாறும்போது spectrum analyser மீளமைக்கப்படும்; பழைய analyser நிலை அடுத்த ஒலி வடிவத்துக்கு எடுத்துச் செல்லப்படாது.
- புதுப்பிப்பு முதலில் RP-Android-Apps-ஐயும் பின்னர் InternetRadio-வையும் பார்க்கும். Android நிறுவியைத் திறப்பதற்கு முன் digest, package, version, canonical HTTPS release URL மற்றும் signer சரிபார்க்கப்படும்.
- ஒலி செயலாக்கம் சாதனத்திலேயே நடைபெறும்.

`PulseEQ-v1.4.apk`-ஐ Android வழியாக நிறுவவும். கேட்கப்பட்டால், பயன்படுத்திய உலாவி அல்லது கோப்பு மேலாளருக்கான **இந்த மூலத்திலிருந்து நிறுவு** அனுமதியை இயக்கி, Android நிறுவியில் உறுதிப்படுத்தவும். அமைதியான நிறுவல் அல்லது Play Protect தவிர்ப்பு இல்லை.
