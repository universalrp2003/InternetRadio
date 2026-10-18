#!/usr/bin/env bash
# Hosted-emulator UI smoke test for the local-only Secret Message preview (test only).
#
# It installs the debug APK on an Android 15 (API 35) emulator and drives the real UI through the
# accessibility tree: NOT SECURE notice, built-in keyboard input, an undelivered local message,
# settings and tone preview, the large square drawing pads (size and fit), the 30-second
# undelivered expiry, drawing, inserting and sending a letter and a picture, and no crash.
# FLAG_SECURE blocks screenshots, not the accessibility tree. This is not a physical-device test.
set -Eeuo pipefail

APK="${1:?usage: run-preview-on-emulator.sh <debug-apk>}"
PACKAGE="com.rpideas.secretmessage.preview"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
AVD_NAME="${ANDROID_AVD_NAME:-secretmessage-preview-smoke}"
IMAGE="${ANDROID_EMULATOR_IMAGE:-system-images;android-35;google_apis;x86_64}"
ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
unset ANDROID_SDK_HOME ANDROID_EMULATOR_HOME
export ANDROID_USER_HOME ANDROID_AVD_HOME
WORK="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/secretmessage-preview-smoke"
mkdir -p "$WORK"
EMULATOR_LOG="$WORK/emulator.log"
UI_XML="$WORK/window.xml"
STEP="setup"

emit_failure() {
  local details="$1"
  details+=$'\n--- crash buffer ---\n'
  details+="$("${ADB:-adb}" logcat -d -b crash 2>/dev/null | tail -n 60 || true)"
  details+=$'\n--- recent log ---\n'
  details+="$("${ADB:-adb}" logcat -d -t 60 2>/dev/null | grep -v -E 'chatty|GnssHAL' | tail -n 40 || true)"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    { echo "## Preview emulator smoke failed at: $STEP"; echo '```text'; printf '%s\n' "$details"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
  fi
  details="$(printf '%s' "$details" | head -c 3400)"
  details="${details//'%'/'%25'}"
  details="${details//$'\r'/'%0D'}"
  details="${details//$'\n'/'%0A'}"
  echo "::error title=secretmessage-preview-emulator ($STEP)::$details"
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
[[ -s "$APK" ]] || fail "Preview APK not found: $APK"
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

wait_for() {
  local attribute="$1" value="$2" mode="${3:-equals}" seconds="${4:-20}" waited=0
  while (( waited < seconds )); do
    app_alive || fail "The preview process is not running while waiting for: $value"
    if dump_ui && find_node "$attribute" "$value" "$mode" >/dev/null; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  fail "Timed out after ${seconds}s waiting for $attribute '$value'. Visible text: $(grep -o 'text="[^"]*"' "$UI_XML" 2>/dev/null | head -n 40 | tr '\n' ' ')"
}

wait_until_gone() {
  local attribute="$1" value="$2" mode="${3:-equals}" seconds="${4:-20}" waited=0
  while (( waited < seconds )); do
    app_alive || fail "The preview process is not running while waiting for '$value' to disappear"
    if dump_ui && ! find_node "$attribute" "$value" "$mode" >/dev/null; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  fail "'$value' was still visible after ${seconds}s"
}

tap() {
  local attribute="$1" value="$2" mode="${3:-equals}" point
  wait_for "$attribute" "$value" "$mode" 15
  point="$(find_node "$attribute" "$value" "$mode")"
  # shellcheck disable=SC2086
  "$ADB" shell input tap $point
  sleep 1
}

# The owner asked for a drawing pad about half the screen tall: require a square pad at least 35% of
# the display height (the 0.2.0 pad was about 19%) with the given tool button fully on-screen below it.
check_large_pad() {
  local pad_desc="$1" tool_text="$2" report
  wait_for content-desc "$pad_desc"
  wait_for text "$tool_text"
  if ! report="$(python3 - "$UI_XML" "$pad_desc" "$tool_text" "$SCREEN_W" "$SCREEN_H" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, pad_desc, tool_text, screen_w, screen_h = sys.argv[1:6]
screen_w, screen_h = int(screen_w), int(screen_h)


def bounds(attribute, value):
    for node in ET.parse(path).getroot().iter("node"):
        if node.get(attribute, "") == value:
            match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if match:
                return tuple(map(int, match.groups()))
    return None


pad = bounds("content-desc", pad_desc)
tool = bounds("text", tool_text)
if pad is None or tool is None:
    print(f"missing node: pad={pad} tool={tool}")
    sys.exit(1)
width, height = pad[2] - pad[0], pad[3] - pad[1]
problems = []
if abs(width - height) > max(6, 0.02 * height):
    problems.append(f"the pad is not square ({width}x{height} px)")
if height < 0.35 * screen_h:
    problems.append(f"the pad height {height} px is below 35% of the {screen_h} px display")
if tool[1] < pad[3]:
    problems.append(f"'{tool_text}' starts above the bottom of the pad ({tool[1]} < {pad[3]})")
if tool[3] > screen_h:
    problems.append(f"'{tool_text}' extends below the display ({tool[3]} > {screen_h})")
summary = (
    f"{pad_desc} {width}x{height} px ({height / screen_h:.0%} of the {screen_w}x{screen_h} display); "
    f"'{tool_text}' at y {tool[1]}-{tool[3]}"
)
if problems:
    print(summary + "; " + "; ".join(problems))
    sys.exit(1)
print(summary)
PY
  )"; then
    fail "Large drawing pad check failed: $report"
  fi
  echo "PASS: $report"
}

# Draw one diagonal stroke across the middle of the pad with the given content description.
swipe_across() {
  local pad_desc="$1" coords
  wait_for content-desc "$pad_desc"
  coords="$(python3 - "$UI_XML" "$pad_desc" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, pad_desc = sys.argv[1:3]
for node in ET.parse(path).getroot().iter("node"):
    if node.get("content-desc", "") == pad_desc:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            w, h = x2 - x1, y2 - y1
            print(x1 + w * 3 // 10, y1 + h * 3 // 10, x1 + w * 7 // 10, y1 + h * 7 // 10)
            sys.exit(0)
sys.exit(1)
PY
  )" || fail "Could not locate '$pad_desc' for drawing"
  # shellcheck disable=SC2086
  "$ADB" shell input swipe $coords 400
  sleep 1
}

STEP="install and launch"
"$ADB" install -r "$APK" > "$WORK/install.log" 2>&1 || fail "$(cat "$WORK/install.log")"
"$ADB" logcat -c
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity" > "$WORK/start.log" 2>&1 || fail "$(cat "$WORK/start.log")"

STEP="disclosures"
wait_for text "NOT SECURE · LOCAL PREVIEW ONLY" equals 40
wait_for text "Pairing not enabled"
wait_for text "Not connected"
wait_for text "NOT SECURE"

STEP="built-in keyboard and send"
tap text "h"
tap text "i"
wait_for text "hi"
tap text "Send"
wait_for text "Not delivered · removed in" contains 10
sent_at=$(date +%s)

STEP="settings and tone preview"
tap content-desc "Preview settings"
wait_for text "Recipient view timer"
wait_for text "60 seconds"
wait_for text "Crystal Pulse"
wait_for text "Notification tone"
tap content-desc "Play Crystal Pulse"
tap text "Back to chat"

STEP="large drawing pads"
tap text "Draw pad"
# Owner request for 1.1.0: the pad opens in Picture mode. The pad controls must fit on a Pixel
# 2-sized screen (an earlier layout pushed them off-screen).
wait_for text "Send picture"
wait_for text "Undo"
check_large_pad "Picture drawing pad" "Send picture"
tap text "Letter"
wait_for text "+ Insert letter"
wait_for content-desc "Medium letter size"
check_large_pad "Letter drawing pad" "+ Insert letter"
tap text "English keyboard"
wait_for text "?123"
if (( $(date +%s) - sent_at < 25 )); then
  # Still inside the 30-second window: the unsent message must not have disappeared early.
  wait_for text "Not delivered · removed in" contains 5
fi

STEP="30-second undelivered expiry"
remaining=$(( 34 - ($(date +%s) - sent_at) ))
(( remaining < 5 )) && remaining=5
wait_until_gone text "Not delivered · removed in" contains "$remaining"
wait_for text "Your preview chat"

STEP="draw, insert and send on the large pads"
tap text "Draw pad"
tap text "Letter"
swipe_across "Letter drawing pad"
tap text "+ Insert letter"
wait_for content-desc "Drawn letter"
tap text "Send"
tap text "Picture"
swipe_across "Picture drawing pad"
tap text "Send picture"
tap text "English keyboard"
wait_for text "Not delivered · removed in" contains 10
wait_for content-desc "Drawn picture"

STEP="crash check"
app_alive || fail "The preview process exited during the smoke test"
if "$ADB" logcat -d -b crash 2>/dev/null | grep -q "$PACKAGE"; then
  fail "The crash buffer contains an entry for $PACKAGE"
fi
echo "PASS: preview launched on API 35, showed its NOT SECURE notice, accepted built-in keyboard input, kept an undelivered local message for about 30 seconds, opened settings, played a tone, showed square drawing pads at least 35% of the display tall with their tools on-screen, sent a drawn letter and a drawn picture, and did not crash."
