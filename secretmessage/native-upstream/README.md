# Upstream OpenMLS dependency-migration candidate (TEST ONLY)

This directory is a separate feasibility lane. It reuses the same isolated in-memory MLS/JNI harness source from `secretmessage/native/src/lib.rs`, but resolves OpenMLS workspace crates from the pinned upstream development commit `e0b21a70a55a9014b8de2772b36bf3d638adad51` instead of the published OpenMLS 0.9.0 crates. That upstream commit uses the newer `hpke-rs` 0.8 dependency line and resolves `libcrux-kem` 0.0.10; it also raises the Rust MSRV to 1.94.

This is an unreleased candidate, not a replacement for the separately pinned 0.9.0 feasibility result. The code is not linked into the Secret Message product app. The CI lane must run the full Cargo/RustSec audit and Android emulator test. Even if those checks pass, this does not establish an official upstream release, an independent audit of the updated graph, physical-device or two-phone behavior, or security/release approval. Do not publish this candidate or use it for sensitive communications.

No local crypto code or provider implementation is patched. If the upstream commit fails compilation, runtime tests, license review, or the full audit, keep it rejected and leave the original 0.9.0 smoke evidence unchanged.
