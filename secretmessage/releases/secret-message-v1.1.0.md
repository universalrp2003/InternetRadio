Secret Message 1.1.0 lets remembered phones call each other, adds contact names, Tamil and English, a guide, and a verified updater.

**Not independently security-reviewed.** No outside security expert has reviewed this app, and the mls-rs library has not had a full third-party audit. Do not rely on it where your safety depends on secrecy.

### What's new

- **Call a remembered phone.** After one chat with an invite, the phone appears under **Remembered phones**. Tap **Chat**: the other phone gets a notification, "Someone you remember wants to chat", and the app asks too; the chat opens on both phones when it is answered. If both phones tap **Chat** at the same time, they connect by themselves. A remembered phone whose key is unchanged connects without comparing the safety code again; a new phone or a changed key still needs the comparison.
- **Names and status.** Give each remembered phone a name (kept on this phone only, until you forget the phone or reset). A green dot shows a phone that is ready for chats now; grey means not reachable now.
- **Ready for chats** (Settings, on by default). The app keeps a small encrypted "doorbell" connection to the relay, shown by a notification that says only "Secret Message" and "Ready for chats". It needs no Google services. For reliable chat requests, tap **Allow background use**; on Xiaomi, Redmi and POCO phones also turn on Autostart (see the guide).
- **Tamil and English.** The app starts in Tamil; switch at the top of the start screen or in Settings. The chat keyboard stays English; drawn letters work in any language.
- **Guide** in Tamil and English (Settings > Guide).
- **Settings** now hold Copy connection report, Reset this phone's key and Forget remembered phones (each asks first).
- **About** with credits, links to the other RP Ideas apps, and a verified updater: it checks the package, version, SHA-256 and signing certificate before Android's installer asks you.
- **Drawing pad:** the same large pad with smaller buttons around it; it opens in Picture mode.

### What the relay can see

As before, the relay sees both phones' IP addresses, when they connect and how much data they send, and the start of each session before the safety-code check; it cannot read messages, drawings or pictures. New in 1.1.0: while a phone is ready for chats, the relay sees that its doorbell is connected, and when a remembered phone calls, it sees that a call went from one IP address to that doorbell. The call itself (which chat room to join) is encrypted with a key only the two phones share, and the relay keeps nothing.

### Limits

- Both phones need 1.1.0 or newer and one chat with each other on 1.1.0 before they can call each other.
- Android may delay chat requests when it restricts background apps, especially on Xiaomi, Redmi and POCO phones without Autostart.
- If either phone loses its connection during a chat, the chat ends on both phones; there is no reconnection yet.

### Install

- `Secret-Message-v1.1.0.apk`: Android 8.0 (API 26) or newer, arm64-v8a, armeabi-v7a, x86 and x86_64. It installs over 1.0.0 and keeps your remembered phones.
- Package `com.rpideas.secretmessage`, signed with the project's own release key; signing certificate SHA-256 `58e370e417cf6f33234689acd5bf379474f5a8c55f79181d9e892a321bc04f08`.
- Permissions: Internet and network state; foreground service (connected device) with change-network-state, which Android requires for that service type; notifications; start after restart; keep awake briefly; ask to ignore battery optimization (only when you tap the button); install updates (Android still asks you); fingerprint or biometric for the optional app lock.

The 1.1.0 test builds were tested between two phones on 18 October 2026: Xiaomi 2406ERN9CI (Android 16) and motorola moto g57 power (Android 17), on mobile data. The first test build exchanged the doorbells in an invite chat, then connected the remembered phones without the code comparison and delivered every call; but both phones had tapped **Chat** at once, and neither could answer the other. That was fixed, and the owner confirmed that calling and answering work correctly in the fixed build. This signed APK is a fresh build of that code; it passed the automated checks (23 unit tests, native build, signature and package checks, and an Android 15 emulator run through the live relay).

## தமிழ்

Secret Message 1.1.0: நினைவில் உள்ள தொலைபேசிகள் ஒன்றையொன்று அழைக்கலாம்; தொலைபேசிகளுக்குப் பெயர், தமிழ்/ஆங்கிலம், வழிகாட்டி, சரிபார்க்கப்பட்ட புதுப்பிப்பு.

**சுயாதீனப் பாதுகாப்பு மதிப்பாய்வு செய்யப்படவில்லை.** வெளிப் பாதுகாப்பு நிபுணர் யாரும் இந்தச் செயலியை மதிப்பாய்வு செய்யவில்லை; mls-rs முழுமையான மூன்றாம் தரப்புத் தணிக்கை செய்யப்படவில்லை.

- **நினைவில் உள்ள தொலைபேசியை அழைக்கலாம்.** அழைப்பிதழ் மூலம் ஒருமுறை உரையாடிய பிறகு, அந்தத் தொலைபேசி “நினைவில் உள்ள தொலைபேசிகள்” பட்டியலில் வரும். “உரையாடு” என்பதைத் தொட்டால் மற்ற தொலைபேசிக்கு அறிவிப்பு செல்லும்; அதை அல்லது “பதிலளி” என்பதைத் தொட்டதும் உரையாடல் திறக்கும். இரண்டு தொலைபேசிகளும் ஒரே நேரத்தில் அழைத்தால் தானாகவே இணையும். சாவி மாறாத தொலைபேசிக்குக் குறியீட்டை மீண்டும் ஒப்பிட வேண்டாம்; புதிய தொலைபேசிக்கும் சாவி மாறிய தொலைபேசிக்கும் ஒப்பீடு தேவை.
- **பெயர்களும் நிலையும்.** ஒவ்வொரு தொலைபேசிக்கும் பெயர் வைக்கலாம். பச்சைப் புள்ளி: இப்போது தயார்; சாம்பல்: இப்போது தொடர்பு கொள்ள முடியாது.
- **உரையாடலுக்குத் தயார்** (அமைப்புகள், இயல்பாக இயக்கத்தில்). Google சேவைகள் தேவையில்லை. “பின்னணியில் இயங்க அனுமதி” என்பதைத் தொடவும்; Xiaomi, Redmi, POCO தொலைபேசிகளில் Autostart-ஐயும் இயக்கவும்.
- **தமிழும் ஆங்கிலமும்.** செயலி தமிழில் தொடங்கும். உரையாடல் விசைப்பலகை ஆங்கிலத்திலேயே இருக்கும்.
- **வழிகாட்டி** (அமைப்புகள் > வழிகாட்டி), **செயலி விவரம்** பகுதியில் சரிபார்க்கப்பட்ட புதுப்பிப்பு, மற்ற RP Ideas செயலிகளின் இணைப்புகள்.
- **வரைபலகை:** அதே பெரிய வரைபலகை, சுற்றியுள்ள பொத்தான்கள் சிறியவை; படம் இயல்பாகத் திறக்கும்.
