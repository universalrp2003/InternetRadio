# Secret Message local UI preview

This is an Android **preview shell**, not the Secret Message product or a secure messenger. It is included as `:secretmessage` so the UI and expiry behavior can be built and tested independently.

## What this preview does

- Renders a chat-shaped screen with an in-app QWERTY/symbol keyboard and a freehand drawing canvas.
- Echoes a submitted text or drawing only into the current screen's in-memory state.
- Removes each local preview item 60 seconds after the local Send/Add action, using Android elapsed-realtime rather than the wall clock.
- Sets Android `FLAG_SECURE` as a best-effort screenshot/recording deterrent; this is not an absolute guarantee.
- Declares no Android permissions in its source manifest. CI checks that the merged APK has no network permissions. In particular, this module has no pairing, signaling, transport, persistence, identity, or cryptographic implementation.

The prominent in-app warning is intentional: **do not enter sensitive content**. The preview does not encrypt, deliver, or receive messages. Its local 60-second timer is only UI behavior and does not demonstrate expiry after authenticated delivery. Preview content is not saved to a database or restored through saved-instance state, but no claim is made about secure memory erasure or operating-system snapshots.

The independent `../native-mls-rs` candidate remains a test-only feasibility harness. It is not linked into this app. The standalone `../android-smoke` APK is also not this app. Passing the candidate or preview tests does not establish security, interoperability, or release readiness.

Build the debug preview with `./gradlew :secretmessage:assembleDebug`; run its pure policy tests with `./gradlew :secretmessage:testDebugUnitTest`. Hosted compilation, unit tests, and the merged-APK network-permission gate passed in [run 38678283393](https://github.com/universalrp2003/InternetRadio/actions/runs/38678283393). No physical-device UX test or connected two-phone test has been performed.
