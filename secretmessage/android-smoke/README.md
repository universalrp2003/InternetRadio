# Secret Message OpenMLS JNI smoke APK (TEST ONLY)

This is a separate, standalone Android test app under `secretmessage/android-smoke/`. It is not included in the Internet Radio app build, is not a Secret Message product APK, and must never be published as a secure messaging app.

The manifest requests no `INTERNET` permission; the app accepts no user messages or identities and does not connect two phones. Its test-only intent calls one JNI method that runs the fixed two-member OpenMLS 0.9.0 in-memory fixture. This exercises native-library loading, JNI symbol binding, and the host-tested protocol flow on Android; both synthetic members are created in the same process, so this is not a transport or peer-to-peer test. Hosted CI run [38589530783](https://github.com/universalrp2003/InternetRadio/actions/runs/38589530783) built and uploaded the APK, installed it on an API 35 x86_64 Android emulator, and completed the fixture successfully. This remains emulator-only feasibility evidence—not physical-device or two-phone validation.

The result is a feasibility signal only. It does not establish real identity binding, Android Keystore behavior, persistent state safety, screenshot prevention, WebRTC connectivity, two-device interoperability, or security approval. RustSec remains a blocking gate; do not integrate this bridge into the product app or send sensitive information through it.

## Build a local debug APK

Use Rust 1.91.0, Android SDK tools, and NDK 27.2.12479018. From the repository root:

```sh
ANDROID_HOME=/path/to/android-sdk \
ANDROID_NDK_HOME=/path/to/android-sdk/ndk/27.2.12479018 \
bash secretmessage/android-smoke/build-android-smoke.sh
```

The script builds the test-only JNI-enabled Rust `cdylib` for arm64-v8a, armeabi-v7a, x86, and x86_64; configures the NDK C compiler, archiver and bindgen sysroot for native-provider experiments; copies the build outputs into the standalone Gradle project; assembles a debug APK; and checks that all four libraries are packaged and the APK has no Internet permission. If `secretmessage/native/Cargo.lock` is absent, it generates a feasibility lockfile and leaves it for review; this does not clear RustSec or imply security approval. Generated `.so`, APK, Gradle, and Rust build outputs are ignored. CI uploads the debug APK as a short-retention feasibility artifact only; no release or store workflow consumes it.

For a separate, isolated dependency experiment, `SECRET_MESSAGE_NATIVE_DIR` can point the same build script at another test-only crate with the same JNI library name/features. `SECRET_MESSAGE_CRYPTO_LABEL` changes only the build log label. The upstream OpenMLS and mls-rs/AWS-LC workflows use this override; neither candidate is linked into the product app, and their APK artifacts are not release inputs.

## Run on a Linux Android emulator

The runtime smoke helper expects an API 35 x86_64 Google APIs image, Android emulator/platform-tools, and KVM acceleration. After building the APK, install those SDK components and run:

```sh
sdkmanager --install platform-tools "platforms;android-35" emulator "system-images;android-35;google_apis;x86_64"
bash secretmessage/android-smoke/run-on-emulator.sh
```

The helper installs the test APK, launches its local smoke path through a test-only intent, and checks the result in logcat. It makes no network calls from the app. The hosted API 35 emulator pass is a runtime feasibility result only—not physical-device testing, two-phone interoperability, or security approval. The aggregate CI gate remains blocked by RustSec; do not integrate this bridge into the product app or send sensitive information through it.
