# Isolated OpenMLS Android feasibility spike

**Not production code. This crate is not yet linked to the Android app, does not expose a JNI/Kotlin API, and does not establish verified identities. Do not use it for real messages.**

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

The script builds arm64-v8a, armeabi-v7a, x86, and x86_64 targets at API 26. It is also run in `.github/workflows/secret-message-mls-spike.yml`. Generated `target/` files and shared libraries are build outputs and must not be committed.

CI records the resolved dependency graph and runs RustSec auditing. The repository-level OpenMLS audit did not cover either crypto provider; an Android build passing is only an integration result, not security approval. Do not enable draft features or the Libcrux provider in this spike. Before considering production use, review the exact generated lockfile, active Android dependency graph, RustSec results, licenses/notices, audit-version delta, identity binding, and independent security review as required by `../../docs/secret-message-security-design.md`.
