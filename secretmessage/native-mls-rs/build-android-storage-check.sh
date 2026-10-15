#!/usr/bin/env bash
set -euo pipefail

NATIVE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
NDK_ROOT="${ANDROID_NDK_HOME:-${SDK_ROOT:+$SDK_ROOT/ndk/$NDK_VERSION}}"
API_LEVEL="${ANDROID_API_LEVEL:-26}"

if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin" ]]; then
  echo "Android SDK/NDK is required for the separate SQLCipher cross-build check." >&2
  exit 2
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
SYSROOT="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

build_target() {
  local target="$1"
  local linker_name="$2"
  local target_env target_env_lower linker bindgen_target output
  target_env="$(printf '%s' "$target" | tr '[:lower:]-' '[:upper:]_')"
  target_env_lower="${target_env,,}"
  linker="$TOOLCHAIN/$linker_name"
  bindgen_target="${linker_name%-clang}"
  if [[ ! -x "$linker" ]]; then
    echo "Required Android linker not found: $linker" >&2
    exit 2
  fi

  export "CARGO_TARGET_${target_env}_LINKER=$linker"
  export "CC_${target_env_lower}=$linker"
  export "AR_${target_env_lower}=$TOOLCHAIN/llvm-ar"
  export "BINDGEN_EXTRA_CLANG_ARGS=--target=$bindgen_target --sysroot=$SYSROOT"

  echo "Cross-building the separate mls-rs SQLCipher feature for $target (Android API $API_LEVEL)."
  (cd "$NATIVE_DIR" && cargo build --locked --release --features "android-jni storage-spike" --target "$target")
  output="$NATIVE_DIR/target/$target/release/libsecretmessage_mls_spike.so"
  if [[ ! -s "$output" ]]; then
    echo "Expected cross-built JNI library was not produced: $output" >&2
    exit 1
  fi
}

build_target aarch64-linux-android "aarch64-linux-android${API_LEVEL}-clang"
build_target armv7-linux-androideabi "armv7a-linux-androideabi${API_LEVEL}-clang"
build_target i686-linux-android "i686-linux-android${API_LEVEL}-clang"
build_target x86_64-linux-android "x86_64-linux-android${API_LEVEL}-clang"

echo "PASS: the optional mls-rs SQLCipher graph, including its separate vendored OpenSSL backend, cross-built for all four Android ABIs beside the AWS-LC MLS provider. This is build-only feasibility evidence; it does not exercise SQLCipher at runtime or Android Keystore key management."
