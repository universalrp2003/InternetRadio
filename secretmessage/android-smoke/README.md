# Secret Message MLS LAN smoke APK (TEST ONLY)

This is a separate standalone Android **test APK** under `secretmessage/android-smoke/`. It is not the Secret Message product app and must not be used for private or sensitive messages or published as a secure messenger.

## What it tests

- **Local MLS smoke:** two synthetic clients exchange fixed test messages in memory on one device.
- **Direct same-Wi-Fi MLS smoke (mls-rs/AWS-LC candidate only):** one device creates a short-lived local invite; the other opens or pastes it and joins. The apps connect directly to a private IPv4 address and exchange only fixed synthetic MLS messages. After the MLS KeyPackage/Welcome exchange, both phones show a 20-digit **safety code**. The testers compare the two screens and tap **Codes match** on both phones before any fixed message is exchanged; **Codes differ** on either phone stops both. The listener accepts one peer and times out after about three minutes. The OpenMLS candidate build hides these LAN controls.
- **Diagnostics:** the screen reports device model, Android version/API, and ABI. “Copy device and test results” copies these details, the test APK version, the local/LAN smoke outcomes and the safety-code step, including the code shown on that phone and its answer. It does not include the invite token, serial number, IMEI, or message content. ADB log tags are `MLS_SMOKE` and `LAN_SMOKE`.

The LAN invite is a `secretmessage://join` link that can be shared through Android's share sheet (including SMS) or pasted into the join field. The app does not read or send SMS itself. SMS carries only a temporary LAN address/port/token, **not chat messages**. Keep this invite private while the test is active; it is a bearer invite and is not identity verification. Both devices must be on the same Wi-Fi network with client-to-client traffic allowed. Guest Wi-Fi isolation, VPNs, or router rules can prevent the connection. There is no public signaling server, relay, TURN, or external chat service in this LAN test.

## Security and scope limits

The MLS clients and identities are created only for this smoke exchange and are discarded afterwards. The LAN fixture uses mls-rs `BasicIdentityProvider`, which accepts any BasicCredential and is explicitly intended for tests; it does not authenticate a real person or device. The safety code is the first 20 bytes of the group's RFC 9420 epoch authenticator, shown as four groups of five digits. Matching codes show that both phones are in the same MLS group, so a relaying device would cause different codes. Because the identities are temporary, this verifies that session only, not a persistent device identity. The app sends no user-entered chat text, stores no group/message history, and does not protect persistent identities. The test is not an audited or release-ready application. **Do not use it for actual private communication.**

Android requires the `INTERNET` permission for TCP sockets, even when the peer is on a private LAN. The test code accepts only private IPv4 addresses and does not contact a public signaling or relay endpoint, but the permission itself is broader than the test's intended route. The SMS/share path uses Android's user-mediated share sheet and does not request `SEND_SMS`.

## Build a local debug APK

Use Rust 1.94.0, Android SDK tools, JDK 17, and NDK 27.2.12479018. From the repository root:

```sh
SECRET_MESSAGE_NATIVE_DIR="$PWD/secretmessage/native-mls-rs" \
SECRET_MESSAGE_CRYPTO_LABEL="mls-rs/AWS-LC" \
SECRET_MESSAGE_LAN_TEST_ENABLED=true \
ANDROID_HOME=/path/to/android-sdk \
ANDROID_NDK_HOME=/path/to/android-sdk/ndk/27.2.12479018 \
bash secretmessage/android-smoke/build-android-smoke.sh
```

The script builds the JNI library for arm64-v8a, armeabi-v7a, x86, and x86_64; packages all four libraries; builds the debug APK; and verifies the LAN socket permission. Generated `.so`, APK, Gradle, and Rust build outputs are ignored. CI uploads this debug APK as a short-retention test artifact; no release/store workflow consumes it.

## Test on two devices

1. Install the same debug APK on both devices and connect both to the same non-guest Wi-Fi. If Android rejects an update over an earlier CI debug APK, uninstall that earlier test APK first; separate hosted builds may use different debug-signing keys.
2. On the first device, tap **Create local invite / start host**, then **Share invite** and choose the SMS or share app. Keep the host app open.
3. On the second device, tap the invite if the SMS app opens the app; otherwise copy/paste the `secretmessage://join?...` URI into the join field. Tap **Join local invite**.
4. Both phones show **Compare the safety code** with 20 digits. Hold the phones side by side and compare every digit. Tap **Codes match** on both phones only if all digits are identical; otherwise tap **Codes differ**. Each phone waits up to two minutes for its user's answer.
5. A successful run should show `PASS` on both devices. Use **Copy device and test results** on each device and paste the reports into the development conversation. Do not send personal message content; this APK has no chat-text feature.
6. Optional stop-path check: run it again and tap **Codes differ** on one phone. Both phones should show `STOPPED`, and no test messages are exchanged.

A PASS demonstrates only that this candidate completed one fixed MLS test exchange over a direct local TCP connection on those devices. It does not prove real identity verification, hostile-network safety, internet P2P, TURN fallback, persistence, Keystore integration, two-device product behavior, or security approval. CI's hosted API 35 emulator continues to exercise only the local in-memory MLS smoke path; the safety-code comparison needs two phones, and host-loopback tests cover its protocol. The first user-reported manual same-Wi-Fi test-only LAN run passed on 2026-10-15 on OPPO CPH1803 (Android 8.1/API 27, arm64-v8a) and Xiaomi 2406ERN9CI (Android 16/API 36, arm64-v8a); only fixed synthetic MLS messages were exchanged, with no user text or chat history sent or stored. The OPPO's local in-memory smoke passed; the Xiaomi's local smoke was not run. This does not establish product readiness, peer identity verification, Internet P2P/TURN, or security approval.