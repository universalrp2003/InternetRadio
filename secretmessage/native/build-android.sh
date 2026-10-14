#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
NDK_ROOT="${ANDROID_NDK_HOME:-${SDK_ROOT:+$SDK_ROOT/ndk/$NDK_VERSION}}"
API_LEVEL="${ANDROID_API_LEVEL:-26}"

if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK_HOME or install NDK $NDK_VERSION under ANDROID_HOME." >&2
  exit 2
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ ! -x "$TOOLCHAIN/aarch64-linux-android${API_LEVEL}-clang" ]]; then
  echo "NDK linker wrappers for API $API_LEVEL were not found in $TOOLCHAIN." >&2
  exit 2
fi

cd "$SCRIPT_DIR"
if [[ ! -f Cargo.lock ]]; then
  echo "Generating a Cargo.lock for this feasibility-only build; this is not security approval."
  cargo generate-lockfile
fi

build_target() {
  local target="$1"
  local linker_name="$2"
  local target_env linker
  target_env="$(printf '%s' "$target" | tr '[:lower:]-' '[:upper:]_')"
  linker="$TOOLCHAIN/$linker_name"
  if [[ ! -x "$linker" ]]; then
    echo "Required Android linker not found: $linker" >&2
    exit 2
  fi
  export "CARGO_TARGET_${target_env}_LINKER=$linker"
  echo "Building test-only JNI-enabled OpenMLS smoke library for $target (Android API $API_LEVEL)"
  cargo build --locked --release --features android-jni --target "$target"
}

build_target aarch64-linux-android "aarch64-linux-android${API_LEVEL}-clang"
build_target armv7-linux-androideabi "armv7a-linux-androideabi${API_LEVEL}-clang"
build_target i686-linux-android "i686-linux-android${API_LEVEL}-clang"
build_target x86_64-linux-android "x86_64-linux-android${API_LEVEL}-clang"
