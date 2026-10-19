#!/usr/bin/env bash
# Builds Secret Message (same Wi-Fi and Internet relay): the mls-rs/AWS-LC JNI library for four
# Android ABIs, then the debug APK (package com.rpideas.secretmessage.debug) and the unsigned release
# APK (com.rpideas.secretmessage; CI signs it with the project's private key), and checks both:
#   - all four native libraries are packaged;
#   - every NativeBridge `external fun` has a matching JNI export in the library;
#   - the merged permissions are exactly the expected set;
#   - package and label are right, and only the debug APK is debuggable.
# Set SECRET_MESSAGE_WIFI_SKIP_NATIVE=true to reuse jniLibs that are already in place.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
NATIVE_DIR="$REPO_ROOT/secretmessage/native-mls-rs"
APP_DIR="$SCRIPT_DIR/app"
JNI_LIBS="$APP_DIR/src/main/jniLibs"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
NDK_ROOT="${ANDROID_NDK_HOME:-${SDK_ROOT:+$SDK_ROOT/ndk/$NDK_VERSION}}"
API_LEVEL="${ANDROID_API_LEVEL:-26}"
SKIP_NATIVE="${SECRET_MESSAGE_WIFI_SKIP_NATIVE:-false}"
BINDGEN_USER_ARGS="${BINDGEN_EXTRA_CLANG_ARGS:-}"
LIB_NAME="libsecretmessage_mls_spike.so"
PACKAGE="com.rpideas.secretmessage"
LABEL="Secret Message"

[[ -n "$SDK_ROOT" ]] || { echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2; exit 2; }
if [[ -z "$NDK_ROOT" || ! -d "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin" ]]; then
  echo "Android NDK not found. Set ANDROID_NDK_HOME or install NDK $NDK_VERSION under the SDK." >&2
  exit 2
fi
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"

build_target() {
  local target="$1" linker_name="$2" abi="$3"
  local target_env target_env_lower linker bindgen_target ndk_sysroot output
  target_env="$(printf '%s' "$target" | tr '[:lower:]-' '[:upper:]_')"
  target_env_lower="${target_env,,}"
  linker="$TOOLCHAIN/$linker_name"
  bindgen_target="${linker_name%-clang}"
  ndk_sysroot="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
  output="$NATIVE_DIR/target/$target/release/$LIB_NAME"
  [[ -x "$linker" ]] || { echo "Required Android linker not found: $linker" >&2; exit 2; }
  export "CARGO_TARGET_${target_env}_LINKER=$linker"
  # AWS-LC's build calls the C compiler directly; cc-rs reads these target-specific variables.
  export "CC_${target_env_lower}=$linker"
  export "AR_${target_env_lower}=$TOOLCHAIN/llvm-ar"
  export "BINDGEN_EXTRA_CLANG_ARGS=${BINDGEN_USER_ARGS}${BINDGEN_USER_ARGS:+ }--target=$bindgen_target --sysroot=$ndk_sysroot"
  echo "Building the mls-rs/AWS-LC JNI library for $target ($abi, Android API $API_LEVEL)"
  (cd "$NATIVE_DIR" && cargo build --locked --release --features android-jni --target "$target")
  [[ -s "$output" ]] || { echo "Expected JNI library was not produced: $output" >&2; exit 1; }
  mkdir -p "$JNI_LIBS/$abi"
  cp "$output" "$JNI_LIBS/$abi/$LIB_NAME"
}

if [[ "$SKIP_NATIVE" != true ]]; then
  if [[ ! -f "$NATIVE_DIR/Cargo.lock" ]]; then
    echo "Generating a Cargo.lock for this test-only build; this is not security approval."
    (cd "$NATIVE_DIR" && cargo generate-lockfile)
  fi
  rm -rf "$JNI_LIBS"
  mkdir -p "$JNI_LIBS"
  build_target aarch64-linux-android "aarch64-linux-android${API_LEVEL}-clang" arm64-v8a
  build_target armv7-linux-androideabi "armv7a-linux-androideabi${API_LEVEL}-clang" armeabi-v7a
  build_target i686-linux-android "i686-linux-android${API_LEVEL}-clang" x86
  build_target x86_64-linux-android "x86_64-linux-android${API_LEVEL}-clang" x86_64
fi

# Every Kotlin `external fun` must have a JNI export, or the app would fail at its first call.
python3 - "$APP_DIR/src/main/java/com/rpideas/secretmessage/wifitest/NativeBridge.kt" \
  "$TOOLCHAIN/llvm-nm" "$JNI_LIBS/x86_64/$LIB_NAME" "$JNI_LIBS/arm64-v8a/$LIB_NAME" <<'PY'
import re
import subprocess
import sys

source, nm, *libraries = sys.argv[1:]
text = open(source, encoding="utf-8").read()
package = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
names = re.findall(r"^\s*external fun (\w+)\(", text, re.M)
if not names:
    raise SystemExit("No external functions were found in NativeBridge.kt")
expected = {"Java_" + package.replace(".", "_") + "_NativeBridge_" + name for name in names}
for library in libraries:
    output = subprocess.run([nm, "-D", "--defined-only", library], check=True, capture_output=True, text=True).stdout
    exported = {line.split()[-1] for line in output.splitlines() if line.strip()}
    missing = sorted(expected - exported)
    if missing:
        raise SystemExit(f"{library} lacks JNI exports for: {', '.join(missing)}")
print(f"All {len(expected)} NativeBridge functions have JNI exports in {len(libraries)} libraries.")
PY

"$REPO_ROOT/gradlew" -p "$SCRIPT_DIR" --no-daemon \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
DEBUG_APK="$APP_DIR/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$APP_DIR/build/outputs/apk/release/app-release-unsigned.apk"
AAPT2="$(find "$SDK_ROOT/build-tools" -type f -name aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
[[ -n "$AAPT2" && -x "$AAPT2" ]] || { echo "aapt2 was not found under $SDK_ROOT/build-tools." >&2; exit 1; }

# check_apk <apk> <package> <label> <debuggable|release>
check_apk() {
  local apk="$1" package="$2" label="$3" kind="$4" badging permissions expected_permissions
  [[ -s "$apk" ]] || { echo "The APK was not produced: $apk" >&2; exit 1; }
  python3 - "$apk" "$LIB_NAME" <<'PY'
import sys
import zipfile

apk, library = sys.argv[1:3]
expected = {f"lib/{abi}/{library}" for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")}
with zipfile.ZipFile(apk) as archive:
    missing = sorted(expected - set(archive.namelist()))
if missing:
    raise SystemExit(f"{apk} is missing native libraries: " + ", ".join(missing))
print(f"{apk} contains all four JNI libraries.")
PY
  badging="$("$AAPT2" dump badging "$apk")"
  grep -Fq "package: name='$package'" <<<"$badging" || { echo "Unexpected package: $(head -n 1 <<<"$badging")" >&2; exit 1; }
  grep -Fq "application-label:'$label'" <<<"$badging" || { echo "$apk is missing its label '$label'." >&2; exit 1; }
  if [[ "$kind" == release ]] && grep -q "application-debuggable" <<<"$badging"; then
    echo "The release APK is marked debuggable." >&2
    exit 1
  fi
  permissions="$(grep -oE "uses-permission: name='[^']+'" <<<"$badging" | sed "s/uses-permission: name='//; s/'$//" | sort -u)"
  expected_permissions="$(printf '%s\n' \
    android.permission.ACCESS_NETWORK_STATE \
    android.permission.CHANGE_NETWORK_STATE \
    android.permission.FOREGROUND_SERVICE \
    android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE \
    android.permission.INTERNET \
    android.permission.POST_NOTIFICATIONS \
    android.permission.RECEIVE_BOOT_COMPLETED \
    android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS \
    android.permission.REQUEST_INSTALL_PACKAGES \
    android.permission.USE_BIOMETRIC \
    android.permission.USE_FINGERPRINT \
    android.permission.WAKE_LOCK \
    "$package.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" | sort -u)"
  if [[ "$permissions" != "$expected_permissions" ]]; then
    echo "Unexpected merged permissions in $apk." >&2
    echo "Expected:" >&2
    printf '%s\n' "$expected_permissions" >&2
    echo "Found:" >&2
    printf '%s\n' "$permissions" >&2
    exit 1
  fi
  echo "$apk: $(head -n 1 <<<"$badging")"
  echo "Merged permissions: $(tr '\n' ' ' <<<"$permissions")"
}

check_apk "$DEBUG_APK" "$PACKAGE.debug" "$LABEL (debug)" debuggable
check_apk "$RELEASE_APK" "$PACKAGE" "$LABEL" release
version="$("$AAPT2" dump badging "$RELEASE_APK" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n 1)"
sha256="$(sha256sum "$DEBUG_APK" | cut -d ' ' -f 1)"
size="$(stat -c %s "$DEBUG_APK")"
echo "Debug APK: $DEBUG_APK ($size bytes, SHA-256 $sha256); unsigned release APK: $RELEASE_APK; version $version"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "apk=$DEBUG_APK"
    echo "release_unsigned=$RELEASE_APK"
    echo "version=$version"
    echo "size=$size"
    echo "sha256=$sha256"
  } >> "$GITHUB_OUTPUT"
fi
