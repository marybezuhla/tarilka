#!/usr/bin/env bash
# Takes the store screenshots on a running emulator: installs the debug APK, loads the invented demo diary
# (DemoActivity), and photographs five screens in Ukrainian and in English into fastlane/.../phoneScreenshots.
#   bash scripts/screenshots/shoot.sh path/to/app-fdroid-debug.apk
set -euo pipefail
APK="$1"; PKG=ua.tarilka; HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
TMP="$(mktemp -d)"

adb install -r "$APK" >/dev/null
# A tidy status bar: fixed clock, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 0941; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4; demo network -e mobile show -e level 4; demo notifications -e visible false

tap() {
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1 || true
    if XY=$(python3 "$HERE/find.py" "$TMP/ui.xml" "$1"); then adb shell input tap $XY; sleep 2; return 0; fi
    sleep 2
  done
  echo "::error::'$1' not found on screen"; adb exec-out screencap -p > "$TMP/missing.png"; return 1
}
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $LANG_DIR/$1"; }
up() { adb shell input swipe 540 1700 540 700 400; sleep 2; }

run() {
  local LOCALE="$1"; LANG_DIR="$2"; local WEIGHT="$3" FOODS="$4" HISTORY="$5"
  OUT="$ROOT/fastlane/metadata/android/$LANG_DIR/images/phoneScreenshots"
  rm -rf "$OUT"; mkdir -p "$OUT"
  adb shell am force-stop $PKG
  adb shell cmd locale set-app-locales $PKG --locales "$LOCALE"
  adb shell am start -W -n $PKG/.DemoActivity >/dev/null
  sleep 8
  shot 1_today
  up; shot 2_meals
  adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.ui.MainActivity >/dev/null; sleep 5
  tap "$HISTORY"; sleep 3; shot 3_history
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap "$WEIGHT"; shot 4_weight
  tap "$FOODS"; shot 5_foods
}

run uk uk Вага Продукти "$(grep -o 'name="history_title">[^<]*' "$ROOT/app/src/main/res/values-uk/strings.xml" | cut -d'>' -f2)"
run en en-US Weight Foods "$(grep -o 'name="history_title">[^<]*' "$ROOT/app/src/main/res/values/strings.xml" | cut -d'>' -f2)"
