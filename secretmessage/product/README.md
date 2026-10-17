# Secret Message local UI preview (0.2.1-preview)

This is an Android **preview shell**, not the Secret Message product or a secure messenger. It is included as `:secretmessage` so the product screens and timer behavior can be built and tested before any secure transport exists. The app is labelled **NOT SECURE** and must not be used for sensitive content.

## What this preview does

Version 0.2.1-preview (code 3) adapts the owner-approved **WhisperPad** product/UI modules from the *New WhisperPad P2P Android Agent Blueprint (MLS-Compatible Edition)*. WhisperPad is the product/UI layer for Secret Message, not a separate app. Version 0.2.1 enlarges the drawing pad: after testing 0.2.0-preview on two phones, the owner asked for a pad about half the screen tall.

- **Built-in input deck (`ui/CustomInputDeck.kt`).** It provides an English QWERTY/symbol keyboard, so the system keyboard is never opened, with a **Draw pad** switch.
  - **Letter mode:** draw a letter or word in any script on a large square pad, then insert it inline at Small/Medium/Large size.
  - **Picture mode:** draw a standalone sketch on the same square pad. Pictures are shown square in the chat.
  - **Pad size (`ui/DrawingPadLayout.kt`):** the pad aims for half of the screen height and stays square, so it is as wide as the screen allows. It shrinks only when the control rows need the room, so Undo, Clear, Delete and Insert/Send stay on-screen. Hints appear inside the empty pad.
  - Line width is adjustable from 2 to 18, with Undo, Clear and Delete. Widths are measured against the original 140 dp pad, so a drawing keeps the same proportions on the large pad, in the small inline letter tiles and in picture bubbles.
  - The deck transmits strokes, not recognized words: there is no handwriting recognition or translation. A single tap is kept as a dot, so marks such as the Tamil pulli survive.
- **Six original notification tones (`audio/`).** They are generated in code as 16-bit 44.1 kHz PCM and played at the notification volume from Settings. No audio files are bundled. The blueprint's sweep and vibrato tones are corrected to accumulate oscillator phase, and every tone fades in and out to avoid clicks.
- **RAM-only chat state with the owner's timer rules (`chat/`).** A pure `ChatStateMachine` drives `ChatViewModel`:
  - A sent message is **undelivered** until an authenticated delivery acknowledgement arrives, and is removed **30 seconds after Send**. There is no offline queue and no resend.
  - The **60–300 second** recipient-view countdown is user-selected in 30-second steps and cannot be turned off. It starts only when the recipient views an authenticated delivery; the sender uses its own configured duration.
  - Purge requests are offered once, duplicates and acknowledgements for unknown messages are ignored, and memory is bounded.
  - All timers use Android elapsed realtime. No wall-clock timestamps are sent; the blueprint's `createdAtEpochMs`/`viewedAtEpochMs` fields were removed.
- **Transport boundary (`transport/SecretMessageMlsTransport.kt`).** This interface comes from the blueprint, and messaging must go through it. Its **only implementation is `UnavailableMlsTransport`**: every send returns false and nothing can arrive.
  - In this preview, every message therefore stays "Not delivered" and disappears after 30 seconds.
  - The recipient-view countdown is implemented and unit-tested, but it never starts here.
  - The separate Wi-Fi TEST build (`../wifi-test`) compiles these same sources and passes its own same-Wi-Fi MLS transport to `ChatViewModel`'s constructor. The chat and settings screens take their wording as a parameter, so this preview's wording and behaviour are unchanged.
- **Optional local app lock (`security/`).** It uses AndroidX BiometricPrompt: fingerprint, face, or the phone's own screen-lock PIN, pattern or password.
  - It locks on launch and when you return to the app. A prompt you dismiss is not reopened in a loop.
  - Android 9–10 (API 28–29) use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`, because AndroidX Biometric rejects the blueprint's `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on those versions.
  - If the phone loses its screen lock, the app lock turns itself off. Removing the screen lock already requires the current credential.
  - Biometric data stays with Android.
- **Privacy hygiene.** `FLAG_SECURE` is set as a best-effort screenshot and recents deterrent, not a guarantee. The manifest adds WhisperPad `tools:node="remove"` rules for network, camera, microphone, storage, media, contacts, location, Bluetooth and notification permissions. Backup and device-to-device transfer are excluded.
  - Only three non-content settings are kept in app-private preferences: view timer, tone and app-lock switch.
  - Message text and drawings are never written to disk or saved-instance state.
- **Merged APK permissions.** The only permissions are install-time `USE_BIOMETRIC` and `USE_FINGERPRINT` (added by AndroidX Biometric for the optional lock), plus AndroidX Core's app-internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. There are no runtime (dangerous) permissions and no network permission. CI fails if any other permission appears.

## Deliberately not included

The blueprints were reviewed against `docs/secret-message-security-design.md`. These parts were not adopted:

- **Homegrown crypto.** No ECDH/HKDF/PBKDF2/AES `CryptoEngine`, 6-character shared secrets, or WebRTC client with its own encryption: the design forbids custom handshakes and ciphers.
- **Hardware transcript signing.** `BiometricAuthManager`'s P-256 "transcript signing" is excluded, because an extra signature outside MLS would be a custom handshake addition. Hardware-backed MLS identity remains a separate design and device-test gate.
- **Unreviewed services.** No signaling server, TURN service, or automatic GitHub release workflow with generated signing keys.
- **Notifications.** `EphemeralNotificationManager` is **deferred**. The preview never receives messages, so posting notifications would only add `POST_NOTIFICATIONS` without a function. When a transport exists, notifications must stay content-free.
- **MLS.** No MLS candidate is linked. The `../native-mls-rs` and `../native` lanes and the standalone `../android-smoke` APK stay separate, test-only feasibility work. Connecting `SecretMessageMlsTransport` to MLS requires the library/provider, identity-binding, dependency, audit and license gates in the security design to pass first.

## Build and test

```sh
./gradlew :secretmessage:testDebugUnitTest :secretmessage:lintDebug :secretmessage:assembleDebug
```

This produces a debug preview only. The release/bundle and four-app publication tasks exclude it.

The dedicated workflow `.github/workflows/secret-message-preview.yml` (test only) does the following:

1. Runs the source contract test.
2. Runs the unit tests and Android lint, and builds the debug APK.
3. Verifies the NOT SECURE label, the package name and a strict merged-permission allowlist, and reports the APK SHA-256.
4. Uploads a 14-day test-only artifact.
5. Runs a UI smoke test on a hosted Android 15 (API 35) emulator (`run-preview-on-emulator.sh`). It checks:
   - the NOT SECURE notice;
   - built-in keyboard input;
   - an undelivered message that is still shown during the 30-second window and then disappears;
   - the settings screen and tone preview;
   - both drawing pads: square, at least 35% of the display tall, with their tool buttons on-screen below the pad;
   - drawing a stroke, inserting it as a letter and sending it, then sending a drawn picture;
   - that the app does not crash.

Run [38842553168](https://github.com/universalrp2003/InternetRadio/actions/runs/38842553168) (commit `a66e0e9`) passed all of these steps for 0.2.1-preview:

- **Unit tests:** 42 tests in 9 classes, including 5 for the pad size, with no failures.
- **Artifact:** `secretmessage-preview-debug-apk-5`, available until 30 October 2026.
- **APK:** `secretmessage-debug.apk`, version 0.2.1-preview, 17,952,802 bytes, SHA-256 `f0f4854a56bf716d8aa717ef96d4c39bb283dac2a2d5e9d1c94dea7f9a1e315b`.
- **Owner device test:** on 16 October 2026 the owner tested 0.2.1-preview and reported liking the larger drawing pad. No screenshots or APK hash were supplied for this build.

For 0.2.0-preview, run [38835582621](https://github.com/universalrp2003/InternetRadio/actions/runs/38835582621) (commit `d93ccca`) passed the checks of that version:

- **Unit tests:** 37 tests in 8 classes, with no failures.
- **Artifact:** `secretmessage-preview-debug-apk-4`, available until 30 October 2026.
- **APK:** `secretmessage-debug.apk`, 17,952,802 bytes, SHA-256 `e04e4e273de5a36d2bce52f8c49a34ad72cef29e6f8d7e5a5c645687df31866b`.
- **Emulator only:** that CI run is not a physical-device test; the owner's device test follows.

An earlier run of the same smoke test found that the drawing-pad controls did not fit on a Pixel 2-sized screen. The layout was fixed before this pass.

**Owner device test of 0.2.0-preview (16 October 2026).** The owner installed `secretmessage-debug.apk` (17.95 MB) from artifact `secretmessage-preview-debug-apk-4` on two phones and reported that it works as expected. The photos and screenshots show:

- Play Protect's “This app looks safe” screen and the app-lock prompt;
- typing with the built-in keyboard, inserted drawn letters and drawn pictures;
- the “Not delivered · removed in …” countdown;
- the settings screen with the view timer and the six tones.

The owner asked for a much larger drawing pad, which 0.2.1-preview adds. The screenshots do not show the APK SHA-256, so the installed bytes are not hash-matched to the artifact. Each phone ran the preview on its own; the preview has no connection between phones.

To try it on a phone, download the artifact from the run page, unzip it, and install `secretmessage-debug.apk`. CI debug builds are signed with per-run debug keys, so uninstall an earlier preview first if Android refuses the update. Passing these checks does not establish security, interoperability or release readiness.
