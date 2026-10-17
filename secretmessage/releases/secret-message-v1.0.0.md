Secret Message sends text, drawn letters (in any language) and drawn pictures between two Android phones. Messages are end-to-end encrypted with MLS (RFC 9420, using mls-rs 0.56 with AWS-LC), are never saved, and disappear from both phones after they are viewed and their timer ends.

**Not independently security-reviewed.** No outside security expert has reviewed this app, and the mls-rs library has not had a full third-party audit. Do not rely on it where your safety depends on secrecy.

### How it works

- One phone taps **Start a chat** and sends the one-time invite by SMS or any app; the other phone opens it and joins.
- Both people compare the same 20-digit **safety code** on both screens and tap **Codes match**. The chat opens only then; **Codes differ** on either phone stops both phones.
- Over the Internet (mobile data or any Wi-Fi), the phones connect through a relay on Cloudflare that only forwards their encrypted data and keeps nothing. **Start on the same Wi-Fi, without the relay** connects two phones on one network directly.
- Messages exist only in the app's memory. A delivered message disappears from both phones 60–300 seconds after it is viewed (you choose the timer); an undelivered one after 30 seconds; everything is removed when the chat ends.
- Phones remember each other's keys and show a red warning if a remembered phone's key changes.
- Built-in English keyboard, a drawing pad for letters and pictures, six notification tones, and an optional app lock (fingerprint or screen lock).
- While a chat is open, a notification that says only "Secret Message" and the chat state keeps it connected when you switch apps. It never shows names or messages.

### What the relay can see

Like any server, the relay sees both phones' IP addresses, when they connect and how much data they send. It also sees the start of each session before the safety-code check: the invite token, the device IDs and the public keys. It cannot read messages, drawings or pictures, and a relay that tried to stand in for the other phone would make the two safety codes differ.

### Limits

- If either phone loses its connection (for example when switching between Wi-Fi and mobile data), the chat ends on both phones; there is no reconnection yet.
- The relay runs on Cloudflare's free plan.
- Message text lives in app memory while a chat is open; screenshots are blocked as far as Android allows.

### Install

- `Secret-Message-v1.0.0.apk`: Android 8.0 (API 26) or newer, arm64-v8a, armeabi-v7a, x86 and x86_64.
- Package `com.rpideas.secretmessage`, signed with the project's own release key; signing certificate SHA-256 `58e370e417cf6f33234689acd5bf379474f5a8c55f79181d9e892a321bc04f08`. Test builds called "Secret Message TEST" or "Secret Message Wi-Fi TEST" are separate apps; uninstall them.
- Permissions: Internet; foreground service (connected device) with change-network-state, which Android requires for that service type; notifications; fingerprint or biometric for the optional app lock.

The same code was tested between phones as test builds on 17 October 2026: Xiaomi 2406ERN9CI (Android 16), motorola moto g57 power (Android 17) and Jio LS1542QWN (Android 12), on the same Wi-Fi, on mobile data and on mixed networks. This exact signed APK passed the automated checks (unit tests, native build, signature and package checks, and an Android 15 emulator run through the live relay); it was published at the owner's request without a separate phone test.
