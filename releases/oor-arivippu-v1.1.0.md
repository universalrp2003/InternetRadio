# Oor Arivippu 1.1.0 — Stable

**Release tag:** `oor-arivippu-v1.1.0`  
**Package:** `com.oorarivippu.voiceannouncer`  
**Version code:** 5  
**APK:** `Oor-Arivippu-v1.1.0.apk`

## English

Fixes for wrong or repeated announcements, a destination chooser for places that share a name, more voices, and a simpler screen.

- **No more “Unnamed Road”.** Placeholders from Android's place lookup, such as “Unnamed Road” / “பெயரில்லாத சாலை”, a bare “Road” or a highway “Service Road”, are never announced. The state or country is never announced as a town. Door numbers (“12/3, …”) and PIN codes are removed from names.
- **One town, one announcement.** A town is no longer announced twice when the lookup switches between Tamil and English spelling (for example “ஆலங்குடி”, then “Alangudi”). Near a town border, a town announced in the last 10 minutes is not announced again. The district word is said once even when the lookup's name already contains it.
- **Choose the right destination.** Many villages share a name (for example பள்ளப்பட்டி). The app now lists every match with its district, state, PIN code and distance, and you pick the right one. **View on map** opens it in your maps app. The alerts speak the chosen place's name, not the text you typed.
- **Defaults:** “Same-town next area” is now off, and the no-internet reminder comes every 5 minutes. If you changed these settings before, your choice is kept.
- **More voices.** Every Android voice your phone has for the language is offered, best quality first, with its quality shown. Choosing a voice plays it. Speech speed: slow, normal or fast. A button opens Android's voice downloads for more voices.
- **Simpler screen.** Long explanations are folded behind **Details**, and each language is chosen on one short line.
- About now links the new RP Ideas app **இரகசிய செய்தி (Secret Message)**.
- Unchanged: place and destination announcements stay paused while there is no internet; offline reminders use only an on-device voice or a short beep. No new Android permissions. The package and RP Ideas signing certificate are the same as before, so the verified in-app updater or a manual install updates 1.0.x; Android still shows its own confirmation and Play Protect decision.

**Testing:** at the owner's request, this update was published as soon as the automatic checks passed, without a separate device test. GitHub Actions ran the app's unit tests on this exact source (including new tests for placeholders, Tamil/English spellings of the same town, the town-border rule, the destination list and the new defaults), Android lint, and the stable-release package, version and signing checks. The published APK is 12,211,781 bytes with SHA-256 `34858e8c6e4831e725ac7503f6613c96bcb896d659ebbdb588dcd139afc7c8b3`.

## தமிழ்

தவறான அல்லது திரும்பத் திரும்ப வரும் அறிவிப்புகளுக்கான திருத்தங்கள், ஒரே பெயருள்ள இடங்களுக்கான இலக்குத் தேர்வு, கூடுதல் குரல்கள், எளிய திரை.

- **“பெயரில்லாத சாலை” இனி இல்லை.** “Unnamed Road” / “பெயரில்லாத சாலை”, வெறும் “சாலை”, நெடுஞ்சாலையின் “சர்வீஸ் சாலை” போன்ற இடம் தேடலின் நிரப்புப் பெயர்கள் அறிவிக்கப்படாது. மாநிலம் அல்லது நாடு ஊராக அறிவிக்கப்படாது. கதவு எண்களும் (“12/3, …”) அஞ்சல் குறியீடுகளும் பெயர்களிலிருந்து நீக்கப்படும்.
- **ஒரு ஊருக்கு ஒரே அறிவிப்பு.** இடம் தேடல் தமிழ், ஆங்கில எழுத்துகளுக்கு இடையே மாறும்போது (எ.கா. “ஆலங்குடி”, பிறகு “Alangudi”) ஊர் இரண்டு முறை அறிவிக்கப்படாது. ஊர் எல்லையில், கடந்த 10 நிமிடங்களில் அறிவித்த ஊர் மீண்டும் அறிவிக்கப்படாது. பெயரிலேயே “மாவட்டம்” இருந்தாலும் அந்தச் சொல் ஒரு முறை மட்டுமே சொல்லப்படும்.
- **சரியான இலக்கைத் தேர்ந்தெடுக்கலாம்.** பல கிராமங்களுக்கு ஒரே பெயர் உண்டு (எ.கா. பள்ளப்பட்டி). இப்போது பொருந்தும் எல்லா இடங்களும் மாவட்டம், மாநிலம், அஞ்சல் குறியீடு, தூரத்துடன் காட்டப்படும்; சரியானதைத் தேர்ந்தெடுக்கவும். **வரைபடத்தில் பார்** அதை உங்கள் வரைபடச் செயலியில் திறக்கும். அறிவிப்புகள் நீங்கள் தட்டச்சு செய்த உரையை அல்ல, தேர்ந்தெடுத்த இடத்தின் பெயரைச் சொல்லும்.
- **இயல்பு அமைப்புகள்:** “ஒரே ஊருக்குள் அடுத்த பகுதி அறிவிப்பு” இப்போது முடக்கத்தில்; இணையமில்லா நினைவூட்டல் 5 நிமிடத்திற்கு ஒருமுறை. முன்பே இவற்றை மாற்றியிருந்தால், உங்கள் தேர்வு அப்படியே இருக்கும்.
- **கூடுதல் குரல்கள்.** உங்கள் தொலைபேசியில் அந்த மொழிக்கு உள்ள எல்லா Android குரல்களும், சிறந்த தரம் முதலில், தரத்துடன் காட்டப்படும். குரலைத் தேர்ந்தெடுத்ததும் அது ஒலிக்கும். பேச்சு வேகம்: மெதுவாக, இயல்பு, வேகமாக. மேலும் குரல்களுக்கு Android குரல் பதிவிறக்கத்தைத் திறக்கும் பொத்தான் உண்டு.
- **எளிய திரை.** நீண்ட விளக்கங்கள் **விவரம்** என்பதன் கீழ் மறைந்திருக்கும்; ஒவ்வொரு மொழியும் ஒரே சிறு வரியில் தேர்ந்தெடுக்கப்படும்.
- செயலி விவரத்தில் புதிய RP Ideas செயலி **இரகசிய செய்தி (Secret Message)**-க்கான இணைப்பு சேர்க்கப்பட்டுள்ளது.
- மாற்றமில்லாதவை: இணையம் இல்லாதபோது இடம் மற்றும் இலக்கு அறிவிப்புகள் நிறுத்தப்பட்டே இருக்கும்; இணையமில்லா நினைவூட்டல்கள் சாதனக் குரல் அல்லது குறுகிய பீப் ஒலியை மட்டுமே பயன்படுத்தும். புதிய Android அனுமதிகள் இல்லை. package-உம் RP Ideas கையொப்பச் சான்றிதழும் முன்பு போலவே; சரிபார்க்கப்பட்ட செயலிக்குள் புதுப்பிப்பு அல்லது நேரடி நிறுவல் 1.0.x-ஐப் புதுப்பிக்கும்; Android தனது உறுதிப்படுத்தலையும் Play Protect முடிவையும் காட்டும்.

**சோதனை:** உரிமையாளரின் கோரிக்கைப்படி, தானியங்கிச் சோதனைகள் வெற்றி பெற்றதும், தனிச் சாதனச் சோதனையின்றி இந்தப் புதுப்பிப்பு வெளியிடப்பட்டது. இதே மூலக் குறியீட்டில் GitHub Actions செயலியின் unit test-களை (நிரப்புப் பெயர்கள், ஒரே ஊரின் தமிழ்/ஆங்கில எழுத்துகள், ஊர் எல்லை விதி, இலக்குப் பட்டியல், புதிய இயல்பு அமைப்புகளுக்கான புதிய சோதனைகள் உட்பட), Android lint, நிலையான வெளியீட்டுக்கான package, பதிப்பு, கையொப்பச் சரிபார்ப்புகள் ஆகியவற்றை இயக்கியது. வெளியிடப்பட்ட APK அளவு 12,211,781 bytes; SHA-256 `34858e8c6e4831e725ac7503f6613c96bcb896d659ebbdb588dcd139afc7c8b3`.
