#!/usr/bin/env bash
# Hosted-emulator smoke test for Secret Message (Internet relay and same Wi-Fi).
#
# It installs the given APK (the signed release APK when CI has one, otherwise the debug APK) on an
# Android 15 (API 35) emulator and drives the real UI through the accessibility tree: the start
# screen, a chat hosted through the live relay until the relay opens the room, a chat hosted on the
# same Wi-Fi, the identity and loaded MLS engine (its protocol name on screen), settings and About,
# and no crash. One emulator cannot run both phones, so the two-phone chat itself is covered by the
# Rust loopback and relay tests and by device tests. This is not a device test.
set -Eeuo pipefail

APK="${1:?usage: run-wifi-test-on-emulator.sh <apk>}"
ACTIVITY="com.rpideas.secretmessage.wifitest.WifiTestActivity"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
# The release APK is com.rpideas.secretmessage and the debug APK com.rpideas.secretmessage.debug.
AAPT2_TOOL="$(find "$SDK_ROOT/build-tools" -type f -name aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
PACKAGE="$("$AAPT2_TOOL" dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
AVD_NAME="${ANDROID_AVD_NAME:-secretmessage-wifi-test-smoke}"
IMAGE="${ANDROID_EMULATOR_IMAGE:-system-images;android-35;google_apis;x86_64}"
ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
unset ANDROID_SDK_HOME ANDROID_EMULATOR_HOME
export ANDROID_USER_HOME ANDROID_AVD_HOME
WORK="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-wifi-test-smoke"
mkdir -p "$WORK"
EMULATOR_LOG="$WORK/emulator.log"
UI_XML="$WORK/window.xml"
STEP="setup"

emit_failure() {
  local details="$1"
  details+=$'\n--- crash buffer ---\n'
  details+="$("${ADB:-adb}" logcat -d -b crash 2>/dev/null | tail -n 60 || true)"
  details+=$'\n--- recent log ---\n'
  details+="$("${ADB:-adb}" logcat -d -t 80 2>/dev/null | grep -v -E 'chatty|GnssHAL' | tail -n 50 || true)"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    { echo "## Wi-Fi test emulator smoke failed at: $STEP"; echo '```text'; printf '%s\n' "$details"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
  fi
  details="$(printf '%s' "$details" | head -c 3400)"
  details="${details//'%'/'%25'}"
  details="${details//$'\r'/'%0D'}"
  details="${details//$'\n'/'%0A'}"
  echo "::error title=secretmessage-wifi-test-emulator ($STEP)::$details"
}

on_error() {
  local status=$?
  trap - ERR
  emit_failure "Command failed (exit $status): $BASH_COMMAND"
  exit "$status"
}
trap on_error ERR

fail() {
  trap - ERR
  emit_failure "$1"
  exit 1
}

SDK_MANAGER="${ANDROID_SDK_MANAGER:-$(find "$SDK_ROOT/cmdline-tools" -type f -name sdkmanager 2>/dev/null | sort -V | tail -n 1 || true)}"
[[ -x "$SDK_MANAGER" ]] || fail "sdkmanager was not found under $SDK_ROOT/cmdline-tools"
[[ -s "$APK" ]] || fail "APK not found: $APK"
[[ "$PACKAGE" == com.rpideas.secretmessage || "$PACKAGE" == com.rpideas.secretmessage.debug ]] \
  || fail "Unexpected package in $APK: '$PACKAGE'"
[[ -e /dev/kvm ]] || fail "KVM is unavailable; refusing a slow software-emulated run."
sudo chmod 666 /dev/kvm

STEP="install emulator packages"
yes 2>/dev/null | head -n 100 | "$SDK_MANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true
"$SDK_MANAGER" --sdk_root="$SDK_ROOT" --install "platform-tools" "platforms;android-35" "emulator" "$IMAGE" > "$WORK/sdkmanager.log" 2>&1 \
  || fail "$(tail -n 40 "$WORK/sdkmanager.log")"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVDMANAGER="$(dirname "$SDK_MANAGER")/avdmanager"

STEP="create AVD"
mkdir -p "$ANDROID_AVD_HOME"
printf 'no\n' | "$AVDMANAGER" create avd --force --name "$AVD_NAME" --package "$IMAGE" --device "pixel_2" \
  --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" > "$WORK/avd.log" 2>&1 || fail "$(cat "$WORK/avd.log")"

STEP="boot emulator"
"$ADB" kill-server >/dev/null 2>&1 || true
"$ADB" start-server >/dev/null 2>&1
emulator_pid=""
cleanup() {
  if [[ -n "$emulator_pid" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
    wait "$emulator_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
"$EMULATOR" -avd "$AVD_NAME" -port 5554 -accel on -no-window -gpu swiftshader_indirect -no-audio \
  -no-boot-anim -no-snapshot-load -no-snapshot-save -camera-back none -wipe-data > "$EMULATOR_LOG" 2>&1 &
emulator_pid=$!
sleep 5
kill -0 "$emulator_pid" 2>/dev/null || fail "Emulator exited early: $(tail -n 60 "$EMULATOR_LOG")"
timeout 120 "$ADB" wait-for-device || fail "ADB did not detect the emulator: $(tail -n 60 "$EMULATOR_LOG")"
booted=false
for _ in $(seq 1 120); do
  if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    booted=true
    break
  fi
  sleep 2
done
[[ "$booted" == true ]] || fail "Emulator boot timed out: $(tail -n 60 "$EMULATOR_LOG")"
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" shell settings put global "$setting" 0
done
"$ADB" shell input keyevent 82 >/dev/null 2>&1 || true
read -r SCREEN_W SCREEN_H < <("$ADB" shell wm size | tr -d '\r' | sed -n 's/^Physical size: \([0-9]*\)x\([0-9]*\)$/\1 \2/p') || true
[[ -n "${SCREEN_W:-}" && -n "${SCREEN_H:-}" ]] || fail "Could not read the emulator display size: $("$ADB" shell wm size 2>&1)"

dump_ui() {
  local attempt
  for attempt in 1 2 3 4 5; do
    if "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 \
      && "$ADB" shell cat /sdcard/window.xml > "$UI_XML" 2>/dev/null \
      && grep -q '<hierarchy' "$UI_XML"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Print "x y" for the first node whose attribute equals (or, with "contains", includes) the value.
find_node() {
  python3 - "$UI_XML" "$1" "$2" "${3:-equals}" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, attribute, value, mode = sys.argv[1:5]
for node in ET.parse(path).getroot().iter("node"):
    actual = node.get(attribute, "")
    if actual == value or (mode == "contains" and value in actual):
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}

app_alive() {
  [[ -n "$("$ADB" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')" ]]
}

visible_text() {
  grep -o 'text="[^"]*"' "$UI_XML" 2>/dev/null | head -n 50 | tr '\n' ' '
}

wait_for() {
  local attribute="$1" value="$2" mode="${3:-equals}" seconds="${4:-20}" waited=0
  while (( waited < seconds )); do
    app_alive || fail "The app process is not running while waiting for: $value"
    if dump_ui && find_node "$attribute" "$value" "$mode" >/dev/null; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  fail "Timed out after ${seconds}s waiting for $attribute '$value'. Visible text: $(visible_text)"
}

# Scroll the connect screen: "down" shows its lower sections, "up" returns to the header.
scroll() {
  local x=$((SCREEN_W / 2)) low=$((SCREEN_H * 3 / 4)) high=$((SCREEN_H / 4))
  if [[ "$1" == down ]]; then
    "$ADB" shell input swipe "$x" "$low" "$x" "$high" 300
  else
    "$ADB" shell input swipe "$x" "$high" "$x" "$low" 300
  fi
  sleep 1
}

# Scroll down until a node matches (at most 12 swipes), for the long settings screen.
scroll_until() {
  local attribute="$1" value="$2" mode="${3:-equals}" swipes=0
  while (( swipes < 12 )); do
    app_alive || fail "The app process is not running while looking for: $value"
    if dump_ui && find_node "$attribute" "$value" "$mode" >/dev/null; then
      return 0
    fi
    scroll down
    swipes=$((swipes + 1))
  done
  fail "Could not find $attribute '$value' after scrolling. Visible text: $(visible_text)"
}

tap() {
  local attribute="$1" value="$2" mode="${3:-equals}" point
  wait_for "$attribute" "$value" "$mode" 15
  point="$(find_node "$attribute" "$value" "$mode")"
  # shellcheck disable=SC2086
  "$ADB" shell input tap $point
  sleep 1
}

STEP="install and launch"
"$ADB" install -r "$APK" > "$WORK/install.log" 2>&1 || fail "$(cat "$WORK/install.log")"
# Grant the notification permission up front so its system dialog does not cover the screen.
"$ADB" shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
"$ADB" logcat -c
"$ADB" shell am start -W -n "$PACKAGE/$ACTIVITY" > "$WORK/start.log" 2>&1 || fail "$(cat "$WORK/start.log")"

STEP="start screen in Tamil, then English"
# From 1.1.0 the app starts in Tamil (owner request); the rest of this test uses English.
wait_for text "உரையாடலைத் தொடங்கு அல்லது இணை" equals 40
wait_for text "முழுக் குறியாக்கம்"
tap text "English"
wait_for text "Start or join a chat" equals 20
wait_for text "End-to-end encrypted"
# The app says plainly that it has not been independently security-reviewed.
wait_for text "Not independently security-reviewed" contains 10

STEP="host a chat through the Internet relay and stop waiting"
tap text "Start a chat"
wait_for text "Waiting for the other phone (up to 3 minutes)" equals 40
wait_for text "secretmessage://chat?relay=" contains 10
# The emulator reached the deployed relay over TLS (OkHttp WebSocket) and the relay opened the room.
wait_for text "Connected to the relay" contains 45
wait_for text "Share invite"
tap text "Stop waiting"
wait_for text "You stopped waiting for the other phone." equals 20

STEP="host a chat on the same Wi-Fi and stop waiting"
scroll down
tap text "Start on the same Wi-Fi, without the relay"
scroll up
wait_for text "secretmessage://chat?host=" contains 20
wait_for text "Same Wi-Fi only" contains 10
tap text "Stop waiting"
wait_for text "You stopped waiting for the other phone." equals 20
wait_for text "Start a chat"
scroll up

STEP="settings"
tap content-desc "Settings"
wait_for text "Recipient view timer"
# The app's wording replaces the preview's ("This preview cannot deliver messages …").
wait_for text "It starts on both phones when the other person views your message." contains
scroll_until text "About Secret Message"
wait_for text "has not been independently security-reviewed" contains

STEP="app settings (1.1.0): ready for chats, this phone, guide and About"
scroll_until text "Ready for chats"
scroll_until text "Remembered phones can call this phone"
# Hosting created this install's identity, stored encrypted under an Android Keystore key.
scroll_until text "key stored encrypted with an Android Keystore key" contains
# This phone's section names the loaded MLS engine and the session protocol (from the native library).
scroll_until text "secretmessage-wifi-chat/1" contains
scroll_until text "Copy connection report"
scroll_until text "Reset this phone's key"
scroll_until text "Open the guide"
tap text "Open the guide"
wait_for text "1. Start a chat with a new phone" equals 15
wait_for text "தமிழ்"
"$ADB" shell input keyevent 4
sleep 1
# About lists the links first and the verified updater last (the helper only scrolls down).
scroll_until text "Source code — InternetRadio"
scroll_until text "Verified GitHub APK updates"
scroll_until text "Check for updates"
"$ADB" shell input keyevent 4
sleep 1
wait_for text "Start a chat"

STEP="crash check"
app_alive || fail "The app exited during the smoke test"
if "$ADB" logcat -d -b crash 2>/dev/null | grep -q "$PACKAGE"; then
  fail "The crash buffer contains an entry for $PACKAGE"
fi
echo "PASS: $PACKAGE launched on API 35, showed its start screen in Tamil and switched to English, showed the not-reviewed note, hosted a chat through the deployed Internet relay (connected over TLS) and on the same Wi-Fi, stopped waiting on request each time, created its Keystore-wrapped identity, loaded the MLS engine (secretmessage-wifi-chat/1), opened settings with ready mode, this phone, the guide and About with the verified updater, and did not crash."
