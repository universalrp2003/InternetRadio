#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AVD_NAME="${ANDROID_AVD_NAME:-secretmessage-mls-smoke}"
ANDROID_EMULATOR_IMAGE="${ANDROID_EMULATOR_IMAGE:-system-images;android-35;google_apis;x86_64}"
ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
# ANDROID_SDK_HOME is deprecated and resolves differently from ANDROID_USER_HOME.
unset ANDROID_SDK_HOME ANDROID_EMULATOR_HOME
export ANDROID_USER_HOME ANDROID_AVD_HOME
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
EMULATOR_LOG="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-android-emulator.log"

emit_failure() {
  local title="$1"
  local details="$2"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      printf '## %s\n' "$title"
      echo '```text'
      printf '%s\n' "$details"
      echo '```'
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  details="${details//'%'/'%25'}"
  details="${details//$'\r'/'%0D'}"
  details="${details//$'\n'/'%0A'}"
  echo "::error title=$title::$details"
}

report_unexpected_failure() {
  local status="$?"
  local details="Failed command: $BASH_COMMAND"
  trap - ERR
  if [[ -f "$EMULATOR_LOG" ]]; then
    details+=$'\n'
    details+="$(tail -n 80 "$EMULATOR_LOG" 2>/dev/null || true)"
  fi
  emit_failure "Android emulator helper failed (exit $status)" "$details"
  exit "$status"
}
trap report_unexpected_failure ERR

if [[ -z "$SDK_ROOT" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 2
fi

SDK_MANAGER="${ANDROID_SDK_MANAGER:-}"
if [[ -z "$SDK_MANAGER" ]]; then
  SDK_MANAGER="$(find "$SDK_ROOT/cmdline-tools" -type f -name sdkmanager 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$SDK_MANAGER" || ! -x "$SDK_MANAGER" ]]; then
  echo "Android sdkmanager was not found under $SDK_ROOT/cmdline-tools." >&2
  exit 2
fi

ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVDMANAGER="$(dirname "$SDK_MANAGER")/avdmanager"
if [[ ! -x "$ADB" || ! -x "$EMULATOR" || ! -x "$AVDMANAGER" ]]; then
  echo "Install Android platform-tools, emulator, and command-line tools before running this test." >&2
  exit 2
fi
if [[ ! -s "$APK" ]]; then
  echo "Standalone test APK not found: $APK. Build it first with build-android-smoke.sh." >&2
  exit 2
fi

if [[ ! -e /dev/kvm ]]; then
  emit_failure "Android emulator cannot run" "KVM is unavailable; refusing a potentially unbounded software-emulation run."
  exit 1
fi
sudo chmod 666 /dev/kvm

AVD_LOG="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-android-avd.log"
mkdir -p "$ANDROID_AVD_HOME"
if ! printf 'no\n' | "$AVDMANAGER" --verbose create avd --force \
  --name "$AVD_NAME" \
  --package "$ANDROID_EMULATOR_IMAGE" \
  --device "pixel_2" \
  --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" > "$AVD_LOG" 2>&1; then
  emit_failure "Android AVD creation failed" "$(cat "$AVD_LOG")"
  exit 1
fi
if [[ ! -f "$ANDROID_AVD_HOME/$AVD_NAME.ini" ]]; then
  locations="$(find "$ANDROID_USER_HOME" -maxdepth 4 -type f -name "$AVD_NAME.ini" -print 2>/dev/null || true)"
  diagnostics=$'AVD manager returned success but did not create the expected AVD registration file.\n'
  diagnostics+="Expected: $ANDROID_AVD_HOME/$AVD_NAME.ini"
  diagnostics+=$'\nFound: '
  diagnostics+="${locations:-none}"
  diagnostics+=$'\nAVD manager output:\n'
  diagnostics+="$(cat "$AVD_LOG")"
  emit_failure "Android AVD registration is missing" "$diagnostics"
  exit 1
fi

"$ADB" kill-server >/dev/null 2>&1 || true
ADB_START_LOG="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-adb-start.log"
if ! "$ADB" start-server > "$ADB_START_LOG" 2>&1; then
  emit_failure "ADB server failed to start" "$(cat "$ADB_START_LOG")"
  exit 1
fi

ACCEL_LOG="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-emulator-accel.log"
if ! "$EMULATOR" -accel-check > "$ACCEL_LOG" 2>&1; then
  emit_failure "Android emulator acceleration check failed" "$(cat "$ACCEL_LOG")"
  exit 1
fi
cat "$ACCEL_LOG"
echo "Starting Android emulator AVD $AVD_NAME from $ANDROID_EMULATOR_IMAGE."
emulator_pid=""
cleanup() {
  if [[ -n "$emulator_pid" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
    wait "$emulator_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

"$EMULATOR" -avd "$AVD_NAME" -port 5554 -accel on \
  -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim \
  -no-snapshot-load -no-snapshot-save -camera-back none -wipe-data -verbose \
  > "$EMULATOR_LOG" 2>&1 &
emulator_pid=$!
sleep 5
if ! kill -0 "$emulator_pid" 2>/dev/null; then
  emit_failure "Android emulator exited before ADB startup" "$(tail -n 120 "$EMULATOR_LOG" 2>/dev/null || true)"
  exit 1
fi

if ! timeout 90 "$ADB" wait-for-device; then
  diagnostics=$'ADB did not detect the Android emulator.\n'
  diagnostics+="$("$ADB" devices -l 2>&1 || true)"
  diagnostics+=$'\n'
  diagnostics+="$(tail -n 120 "$EMULATOR_LOG" 2>/dev/null || true)"
  emit_failure "ADB did not detect the Android emulator" "$diagnostics"
  exit 1
fi
booted=false
for attempt in $(seq 1 90); do
  boot_complete="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot_complete" == "1" ]]; then
    booted=true
    break
  fi
  sleep 2
done
if [[ "$booted" != true ]]; then
  diagnostics=$'Android emulator did not finish booting.\n'
  diagnostics+="$("$ADB" logcat -d -t 120 2>&1 || true)"
  diagnostics+=$'\n'
  diagnostics+="$(tail -n 120 "$EMULATOR_LOG" 2>/dev/null || true)"
  emit_failure "Android emulator boot timed out" "$diagnostics"
  exit 1
fi

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" install -r "$APK"
"$ADB" logcat -c
"$ADB" shell am start -W \
  -n com.rpideas.secretmessage.smoke/.MainActivity \
  --ez run_mls_smoke true

smoke_result=""
for attempt in $(seq 1 90); do
  log_output="$("$ADB" logcat -d -s MLS_SMOKE:I '*:S' 2>/dev/null || true)"
  if grep -Fq 'PASS:' <<< "$log_output"; then
    grep -F 'PASS:' <<< "$log_output" | tail -n 1
    smoke_result="pass"
    break
  fi
  if grep -Fq 'FAIL:' <<< "$log_output"; then
    grep -F 'FAIL:' <<< "$log_output" | tail -n 1 >&2
    smoke_result="fail"
    break
  fi
  sleep 2
done
if [[ "$smoke_result" != "pass" ]]; then
  diagnostics=$'The JNI smoke test did not report PASS in the Android emulator.\n'
  diagnostics+="$("$ADB" logcat -d -t 160 2>&1 || true)"
  diagnostics+=$'\n'
  diagnostics+="$(tail -n 120 "$EMULATOR_LOG" 2>/dev/null || true)"
  emit_failure "Android JNI smoke test failed in emulator" "$diagnostics"
  exit 1
fi

echo "PASS: Android emulator loaded the native library and completed the local two-member MLS fixture."
