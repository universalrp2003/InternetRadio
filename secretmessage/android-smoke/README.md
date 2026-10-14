# Secret Message OpenMLS JNI smoke APK (TEST ONLY)

This is a separate, standalone Android test app under `secretmessage/android-smoke/`. It is not included in the Internet Radio app build, is not a Secret Message product APK, and must never be published as a secure messaging app.

The manifest requests no `INTERNET` permission; the app accepts no user messages or identities and does not connect two phones. Once built and installed, pressing **Run local MLS smoke test** is intended to call one JNI method that runs the existing fixed two-member OpenMLS 0.9.0 in-memory fixture on that device. This is intended to exercise native-library loading, JNI symbol binding, and the host-tested protocol flow on Android. Both synthetic members are created in the same process; this is not a transport or peer-to-peer test. CI run [38576680334](https://github.com/universalrp2003/InternetRadio/actions/runs/38576680334) compiled the JNI bridge, built the APK, and verified the packaged ABIs and absence of Internet permission. That run did not install or execute the APK. The workflow is now being extended to run this APK in a hosted Android emulator; a successful emulator run would still not replace physical-device testing.

The result is a feasibility signal only. It does not establish real identity binding, Android Keystore behavior, persistent state safety, screenshot prevention, WebRTC connectivity, two-device interoperability, or security approval. RustSec remains a blocking gate; do not integrate this bridge into the product app or send sensitive information through it.

## Build a local debug APK

Use Rust 1.91.0, Android SDK tools, and NDK 27.2.12479018. From the repository root:

```sh
ANDROID_HOME=/path/to/android-sdk \
ANDROID_NDK_HOME=/path/to/android-sdk/ndk/27.2.12479018 \
bash secretmessage/android-smoke/build-android-smoke.sh
```

The script builds the test-only JNI-enabled Rust `cdylib` for arm64-v8a, armeabi-v7a, x86, and x86_64; copies those build outputs into the standalone Gradle project; assembles a debug APK; and checks that all four libraries are packaged and the APK has no Internet permission. If `secretmessage/native/Cargo.lock` is absent, it generates a feasibility lockfile and leaves it for review; this does not clear RustSec or imply security approval. Generated `.so`, APK, Gradle, and Rust build outputs are ignored. CI uploads the debug APK as a short-retention feasibility artifact only; no release or store workflow consumes it.

## Run on a Linux Android emulator

The runtime smoke helper expects an API 35 x86_64 Google APIs image, Android emulator/platform-tools, and KVM acceleration. After building the APK, install those SDK components and run:

```sh
sdkmanager --install platform-tools emulator "system-images;android-35;google_apis;x86_64"
bash secretmessage/android-smoke/run-on-emulator.sh
```

The helper installs the test APK, launches its local smoke button through a test-only intent, and checks the result in logcat. It makes no network calls from the app. Emulator success would be a runtime feasibility result only—not physical-device testing, two-phone interoperability, or security approval.
