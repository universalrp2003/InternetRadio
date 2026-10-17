# Secret Message TEST build: Internet relay and same Wi-Fi (test only, not security-reviewed)

This directory builds **Secret Message TEST** (`com.rpideas.secretmessage.wifitest`, version `0.4.0-internet-test`): the owner-approved WhisperPad product screens from `../product` (built-in keyboard, half-screen drawing pad, tones, timers, app lock) connected to a second phone through the mls-rs/AWS-LC MLS session that the LAN smoke test APK (`../android-smoke`) proved on phones: **over the Internet** through the owner's test relay (`../relay`), or directly on the **same Wi-Fi**. On 17 October 2026 the owner approved this as a separate test build of the real app and chose to keep a chat connected while a person switches apps (a notification that shows no names or messages). Version 0.3.0-wifi-test (same Wi-Fi only, named "Secret Message Wi-Fi TEST") passed its first two-phone test that day; the owner then set up the free Cloudflare account for the relay, and 0.4.0 adds the Internet path. It is debug-only, never published, and must not be used for private messages: no security expert has reviewed it, and the mls-rs library itself has not had a full third-party audit.

The offline preview (`../product`, `com.rpideas.secretmessage.preview`) is unchanged in behaviour: it still has no network permission, no native library and only the unconnected transport, and its own workflow keeps checking that. This build compiles the same product sources and adds the Wi-Fi connection here; the two can be installed side by side.

## How a chat works

1. **Start a chat** on one phone. It picks a random room on the relay, connects to it and shows a one-time `secretmessage://chat?relay=…` invite; it waits up to three minutes. (**Start on the same Wi-Fi, without the relay** instead listens on the phone's private Wi-Fi address and a random port, as in 0.3.0, and shows a `secretmessage://chat?host=…` invite.) **Share invite** sends it by SMS or any app; you may switch apps to send it, because the connection service keeps the app reachable.
2. On the other phone, open the invite (if the messaging app makes it a link) and tap **Join this chat**, or copy the whole link and tap **Paste invite and join**.
3. The phones run the MLS KeyPackage/Welcome exchange and both show the same **20-digit safety code**, derived from the group's RFC 9420 epoch authenticator, plus whether the other phone is new, remembered or has a changed key (red warning). Both people compare every digit and tap **Codes match**; **Codes differ** on either phone stops both phones at once. Each person has two minutes.
4. After both confirm, the chat screen opens. Text, drawn letters and pictures travel only inside MLS application messages. The product's own rules apply on both phones: an undelivered message is removed after 30 seconds, and a delivered message disappears from both phones when its 60–300-second view timer ends (it starts when the recipient views it). Nothing is saved.
5. **End** (top of the chat) ends it on both phones; Back leaves it open in the background. A chat also ends after 60 minutes, when either phone closes the app, or when the connection drops, and all messages are then removed from memory.

**Copy test report** puts counts only (no text or drawings) on the clipboard: version, device, how the phones connected (for the relay: how long connecting and pairing took and how the connection ended), the safety-code step, how the chat ended and how many messages and notices of each kind passed.

## Over the Internet (0.4.0-internet-test)

Both phones open a TLS WebSocket (OkHttp) to `wss://secretmessage-relay.universalrp2003.workers.dev`, the relay in `../relay` on the owner's free Cloudflare account, for the same random room (16 random bytes from the inviting phone). When both are in, the relay says `paired` and from then on forwards bytes between them unchanged. The native session is the same one the same-Wi-Fi chat uses: it runs over one end of a local socket pair (`nativeOpenRelayPipe`), and `RelayLink` copies bytes between the other end and the WebSocket, so the relay carries exactly the frames a Wi-Fi connection would. OkHttp pings every 20 seconds keep mobile-network connections open (Cloudflare answers them without waking the relay) and notice a dead connection.

The relay cannot read messages, drawings or pictures (they are MLS application messages), and a relay that tried to stand in for the other phone would make the two safety codes differ. Like any server it sees both phones' IP addresses and when and how much they send, and it sees the start of the session in the clear: the invite token, the device IDs in the MLS credentials and the public keys exchanged before the safety-code check. It stores nothing and keeps no logs. This relay-first design is an owner-approved test deviation from the design document's direct-first WebRTC plan; direct connections can be added later.

If either phone loses its connection (for example, switching between Wi-Fi and mobile data), the relay disconnects the other phone and the chat ends on both; there is no reconnection yet. Invites are version 2 (`v=2`) for both kinds, so 0.3.0 phones refuse them with a clear "different test build" message.

## What changed in the shared code

- **Native session (`../native-mls-rs`):** a `LanProfile` selects the protocol name and limits. The smoke test APK keeps `secretmessage-lan-test/6` and its limits; this build uses `secretmessage-wifi-chat/1`, messages of up to 96 KiB of encoded text, 20,000 messages per phone and a 60-minute chat, so neither app can join the other's invite. A waiting host can be stopped. New JNI exports for `com.rpideas.secretmessage.wifitest.NativeBridge` call the same helpers as the smoke APK's exports.
- **Product (`../product`):** `ChatViewModel` takes its transport as an optional constructor argument (the preview still uses `UnavailableMlsTransport`) and clears all messages when a connected session ends; the chat and settings screens take their wording as a parameter (the preview's wording is unchanged) and can show an **End** button.
- **Native session, relay (0.4.0):** the session code runs over any stream with timeouts and a write shutdown (a TCP socket or one end of a Unix socket pair). `open_relay_pipe` creates the socket pair in safe Rust and hands the app its end as a file descriptor (`IntoRawFd`); `run_relay_mls_host`/`run_relay_mls_join` run the unchanged session over the other end. Host tests run a full chat through a stand-in relay that splits the bytes into uneven pieces, and check that a lost relay connection stops both phones.
- **This build:** `RelayLink` bridges the socket pair and the relay's WebSocket. `PayloadCodec` turns each chat payload (text, drawn letters, pictures, delivery/view/removal notices) into a small versioned binary format sent as Base64 inside one MLS application message, and checks every incoming payload against the product's limits. `LanChatSession` implements the product's `SecretMessageMlsTransport`. `ChatConnectionService` is a `connectedDevice` foreground service whose notification says only "Secret Message" and the chat state. The identity (Keystore-wrapped software key) and remembered phones work as in the smoke test APK.

## Permissions

`INTERNET` (direct TCP to a private IPv4 address on the same Wi-Fi, and the TLS WebSocket to the owner's test relay, the only server the app contacts), `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` with `CHANGE_NETWORK_STATE` (Android 14+ requires it for that service type; the app changes no network settings), `POST_NOTIFICATIONS` (so the service's notification can appear), plus the product's `USE_BIOMETRIC`/`USE_FINGERPRINT` and AndroidX's internal receiver permission. The build script fails if the merged APK has any other permission.

## Build

With Rust 1.94.0 and the four Android targets, JDK 17, the Android SDK and NDK 27.2.12479018, from the repository root:

```sh
ANDROID_HOME=/path/to/android-sdk \
ANDROID_NDK_HOME=/path/to/android-sdk/ndk/27.2.12479018 \
bash secretmessage/wifi-test/build-wifi-test.sh
```

It builds the JNI library for arm64-v8a, armeabi-v7a, x86 and x86_64, runs the unit tests, builds the debug APK and lint report, and checks the packaged libraries, the JNI exports for every `NativeBridge` function, the label, the package and the exact permission set. The workflow `.github/workflows/secret-message-wifi-test.yml` does the same, uploads the APK as a 14-day test artifact (`secretmessage-wifi-test-apk-<run>`), reports its SHA-256, and runs `run-wifi-test-on-emulator.sh` on an API 35 emulator (notice, engine loaded, identity created, hosting through the deployed relay until it reports the room open, stopping, hosting on the emulator's private address, stopping, settings, no crash). One emulator cannot run both phones; the two-phone chat is covered by the Rust loopback and relay tests and by device tests.

For 0.3.0-wifi-test, run [39030739655](https://github.com/universalrp2003/InternetRadio/actions/runs/39030739655) (commit `dfe61ef`) passed every step; its APK is artifact `secretmessage-wifi-test-apk-2` (a ZIP of about 24 MB, available until 31 October 2026) containing `app-debug.apk`, 34,241,431 bytes, SHA-256 `0c6c8f6860b9d97537cbd18338dc8f98499020ec57ee0f1147351156ee084359`. The ZIP's own digest is not the APK's.

Each CI build has its own debug signing key, so uninstall an earlier test APK (including "Secret Message Wi-Fi TEST") before installing a newer one; that also resets this build's test identity and remembered phones.

## Two-phone tests

0.4.0-internet-test has not been tested on phones yet.

0.3.0-wifi-test: on 17 October 2026 the owner installed the APK from run 39030739655 on the Xiaomi 2406ERN9CI (Android 16/API 36, host, device ID `aed4 db91 1dd3 e645`) and a motorola moto g57 power (Android 17/API 37, guest, device ID `8e91 ab73 d045 861a`), both arm64-v8a, and shared both phones' copied reports. Both listed the other as new, confirmed `60287 06713 31275 51096` and reached PASS. In a chat of 3 min 5 s each phone sent three messages: the Motorola two with text and one with drawn letters, the Xiaomi two with text and one picture. Each phone counted the other's three messages as received, sent a delivery notice and a view notice for each, and got the same back. The removal notices (the Motorola sent 5 and received 2, the Xiaomi the reverse) show that all six messages reached the end of their view timers and disappeared from both phones before the chat ended; one was removed by both phones at the same moment. The Motorola then tapped **End**, and the Xiaomi reported "Ended on the other phone". The MLS session acknowledged every payload; none was refused and no send failed. Both phones now remember each other, and notifications were allowed on both. The reports hold counts only; the owner then confirmed that the drawn letter and the picture looked right on the other phone and that the chat stayed open while a phone switched apps.

## Limits

The Internet path always goes through the owner's test relay (no direct Internet connection yet) and ends if either phone's connection drops; the relay is not security-reviewed and runs on Cloudflare's free plan (100,000 requests a day). The MLS layer still accepts any BasicCredential; the safety-code comparison and remembered keys are the identity check. The signing key is a software key wrapped by Android Keystore, not a hardware-bound key. Message text lives in app memory as Java/Kotlin objects, which cannot be wiped. `FLAG_SECURE` is a best-effort screenshot deterrent. This build does not satisfy any release gate in `docs/secret-message-security-design.md`.
