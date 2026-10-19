# Isolated OpenMLS Android feasibility spike

**Not production code. A minimal JNI entry point is enabled only for the separate test APK in `../android-smoke/`; this crate is not linked to the Android product app and does not establish verified identities. The test APK is local-only feasibility scaffolding. Do not use it for real messages.**

The crate pins OpenMLS 0.9.0 and exercises one narrowly scoped flow using the RFC 9420 mandatory-to-implement X25519/AES-128-GCM/SHA-256/Ed25519 ciphersuite:

1. Create separate in-memory Alice and Bob MLS providers and test credentials.
2. Serialize Bob's public KeyPackage; validate it at Alice; create a two-member group and serialize a Welcome.
3. Join Bob to the group and round-trip private MLS application messages in both directions through serialized message bytes.
4. Assert that the unique test plaintext is absent from each serialized application message.

The BasicCredential labels are fixture values, not trusted names. This does not test QR/safety-code verification, identity-key persistence, Android Keystore integration, transport over WebRTC, message expiry, reconnect behavior, or production storage. It creates no public signaling/relay service and writes no message or group state to disk.

## Build and test

Use Rust 1.91.0. For host tests:

```sh
cargo test --locked --features spike-harness
cargo clippy --locked --all-targets --features spike-harness -- -D warnings
```

For the Android native-link smoke build, install Android NDK 27.2.12479018, set `ANDROID_NDK_HOME` (or `ANDROID_HOME`), then run:

```sh
bash build-android.sh
```

The script builds the JNI-enabled test library for arm64-v8a, armeabi-v7a, x86, and x86_64 at API 26. If `Cargo.lock` is absent, it first generates a feasibility lockfile and leaves it for review; that does not clear RustSec or imply security approval. It is also run in `.github/workflows/secret-message-mls-spike.yml`. To package the separate debug APK, run `bash ../android-smoke/build-android-smoke.sh` from this directory. The OpenMLS candidate keeps the LAN controls disabled and invokes only the fixed in-memory smoke test; it does not call networking code. The shared standalone APK manifest includes `INTERNET` permission because the separate mls-rs/AWS-LC candidate enables a private-LAN socket test. Neither candidate is part of the product app or release workflow. Generated `target/` files, copied `.so` libraries, and APKs are build outputs and must not be committed.

CI records the resolved dependency graph and runs RustSec auditing. The repository-level OpenMLS audit did not cover either crypto provider; an Android build passing is only an integration result, not security approval. Do not enable draft features or the Libcrux provider in this spike. Before considering production use, review the exact generated lockfile, active Android dependency graph, RustSec results, licenses/notices, audit-version delta, identity binding, and independent security review as required by `../../docs/secret-message-security-design.md`.
