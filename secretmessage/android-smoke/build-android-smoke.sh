#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
NATIVE_DIR="${SECRET_MESSAGE_NATIVE_DIR:-$REPO_ROOT/secretmessage/native}"
APP_DIR="$SCRIPT_DIR/app"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
NDK_ROOT="${ANDROID_NDK_HOME:-${SDK_ROOT:+$SDK_ROOT/ndk/$NDK_VERSION}}"
API_LEVEL="${ANDROID_API_LEVEL:-26}"
CRYPTO_LABEL="${SECRET_MESSAGE_CRYPTO_LABEL:-OpenMLS}"
LAN_TEST_ENABLED="${SECRET_MESSAGE_LAN_TEST_ENABLED:-false}"
BINDGEN_USER_ARGS="${BINDGEN_EXTRA_CLANG_ARGS:-}"
if [[ "$LAN_TEST_ENABLED" != true && "$LAN_TEST_ENABLED" != false ]]; then
  echo "SECRET_MESSAGE_LAN_TEST_ENABLED must be true or false." >&2
  exit 2
fi
if [[ "$LAN_TEST_ENABLED" == true && ! -f "$NATIVE_DIR/src/spike_harness/lan_smoke.rs" ]]; then
  echo "The LAN test UI requires the mls-rs candidate native crate." >&2
  exit 2
fi

if [[ -z "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi
if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK_HOME or install NDK $NDK_VERSION under the SDK." >&2
  exit 2
fi

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ ! -x "$TOOLCHAIN/aarch64-linux-android${API_LEVEL}-clang" ]]; then
  echo "NDK linker wrappers for Android API $API_LEVEL were not found in $TOOLCHAIN." >&2
  exit 2
fi

if [[ ! -f "$NATIVE_DIR/Cargo.lock" ]]; then
  echo "Generating a Cargo.lock for this feasibility-only build; this is not security approval."
  (cd "$NATIVE_DIR" && cargo generate-lockfile)
fi

# These are test-only build outputs and are ignored by Git.
JNI_LIBS="$APP_DIR/src/main/jniLibs"
rm -rf "$JNI_LIBS"
mkdir -p "$JNI_LIBS"

build_target() {
  local target="$1"
  local linker_name="$2"
  local abi="$3"
  local target_env linker output
  target_env="$(printf '%s' "$target" | tr '[:lower:]-' '[:upper:]_')"
  target_env_lower="${target_env,,}"
  linker="$TOOLCHAIN/$linker_name"
  bindgen_target="${linker_name%-clang}"
  ndk_sysroot="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
  output="$NATIVE_DIR/target/$target/release/libsecretmessage_mls_spike.so"
  if [[ ! -x "$linker" ]]; then
    echo "Required Android linker not found: $linker" >&2
    exit 2
  fi

  export "CARGO_TARGET_${target_env}_LINKER=$linker"
  # Native crypto crates such as AWS-LC also invoke the C compiler directly;
  # cc-rs uses these target-specific variables rather than Cargo's linker setting.
  export "CC_${target_env_lower}=$linker"
  export "AR_${target_env_lower}=$TOOLCHAIN/llvm-ar"
  export "BINDGEN_EXTRA_CLANG_ARGS=${BINDGEN_USER_ARGS}${BINDGEN_USER_ARGS:+ }--target=$bindgen_target --sysroot=$ndk_sysroot"
  echo "Building JNI-enabled $CRYPTO_LABEL smoke library for $target ($abi, Android API $API_LEVEL)"
  (cd "$NATIVE_DIR" && cargo build --locked --release --features android-jni --target "$target")
  if [[ ! -s "$output" ]]; then
    echo "Expected JNI cdylib was not produced: $output" >&2
    exit 1
  fi
  mkdir -p "$JNI_LIBS/$abi"
  cp "$output" "$JNI_LIBS/$abi/libsecretmessage_mls_spike.so"
}

build_target aarch64-linux-android "aarch64-linux-android${API_LEVEL}-clang" arm64-v8a
build_target armv7-linux-androideabi "armv7a-linux-androideabi${API_LEVEL}-clang" armeabi-v7a
build_target i686-linux-android "i686-linux-android${API_LEVEL}-clang" x86
build_target x86_64-linux-android "x86_64-linux-android${API_LEVEL}-clang" x86_64

if ! grep -q 'android.permission.INTERNET' "$APP_DIR/src/main/AndroidManifest.xml"; then
  echo "The same-Wi-Fi MLS LAN test needs INTERNET permission for direct TCP sockets." >&2
  exit 1
fi

gradle_args=()
if [[ "$LAN_TEST_ENABLED" == true ]]; then
  gradle_args+=("-PlanTestEnabled=true")
fi
"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" "${gradle_args[@]}" :app:assembleDebug --no-daemon
APK="$APP_DIR/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "Standalone Android smoke APK was not produced: $APK" >&2
  exit 1
fi

python3 - "$APK" <<'PY'
import sys
import zipfile

apk = sys.argv[1]
expected = {
    "lib/arm64-v8a/libsecretmessage_mls_spike.so",
    "lib/armeabi-v7a/libsecretmessage_mls_spike.so",
    "lib/x86/libsecretmessage_mls_spike.so",
    "lib/x86_64/libsecretmessage_mls_spike.so",
}
with zipfile.ZipFile(apk) as archive:
    entries = set(archive.namelist())
missing = sorted(expected - entries)
if missing:
    raise SystemExit("APK is missing native ABI libraries: " + ", ".join(missing))
print("APK contains all four JNI libraries.")
PY

AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt 2>/dev/null | sort -V | tail -n 1 || true)"
if [[ -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo "aapt was not found under $SDK_ROOT/build-tools; cannot verify merged APK permissions." >&2
  exit 1
fi
permission_dump="$("$AAPT" dump permissions "$APK")"
if ! grep -q 'android.permission.INTERNET' <<< "$permission_dump"; then
  echo "Merged LAN smoke APK is missing INTERNET permission for local TCP sockets:" >&2
  printf '%s\n' "$permission_dump" >&2
  exit 1
fi

echo "Standalone test-only APK: $APK"
echo "Verified: all four native ABIs packaged; INTERNET permission is present for the LAN-only test path."
