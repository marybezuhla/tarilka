#!/usr/bin/env bash
# Takes the store screenshots on a running emulator: installs the debug APK, loads the invented demo diary
# (DemoActivity), and photographs ten screens in Ukrainian and in English into fastlane/.../phoneScreenshots.
# The best eight come first (Komora shows the first eight): Today by day and by night (the default Beeswax and
# Hutsul themes), the meals, adding food, history, its charts, weight, the themes; then foods and options.
#   bash scripts/screenshots/shoot.sh path/to/app-fdroid-debug.apk
set -euo pipefail
APK="$1"; PKG=ua.tarilka; HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$HERE/../.."
TMP="$(mktemp -d)"

adb install -r "$APK" >/dev/null
# A tidy status bar: fixed clock, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 1405; demo battery -e level 100 -e plugged false
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
up() { adb shell input swipe 540 1900 540 900 600; sleep 2; }
# Scrolls until the label is on screen, then brings it up near the top.
to_top() {
  for i in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1 || true
    if XY=$(python3 "$HERE/find.py" "$TMP/ui.xml" "$1"); then
      Y=${XY#* }; [ "$Y" -gt 420 ] && adb shell input swipe 540 "$Y" 540 380 900; sleep 2; return 0
    fi
    up
  done
  echo "::error::'$1' not found while scrolling"; return 1
}
# The keyboard, if a search box opened it, is not part of the picture.
no_keyboard() { adb shell dumpsys input_method | grep -q 'mInputShown=true' && adb shell input keyevent KEYCODE_BACK; sleep 1; return 0; }
start_app() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.ui.MainActivity >/dev/null; sleep 6; }

run() {
  local LOCALE="$1"; LANG_DIR="$2"; local WEIGHT="$3" FOODS="$4" HISTORY="$5" OPTIONS="$6" DAY_THEME="$7" BREAKFAST="$8" ADD="$9"
  OUT="$ROOT/fastlane/metadata/android/$LANG_DIR/images/phoneScreenshots"
  rm -rf "$OUT"; mkdir -p "$OUT"
  adb shell am force-stop $PKG
  # The whole phone in this language, as people have it (a restart of the Android framework).
  adb root >/dev/null; sleep 3
  adb shell "setprop persist.sys.locale $LOCALE; setprop ctl.restart zygote"
  sleep 10; adb wait-for-device
  for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 3; done
  sleep 15
  demo enter; demo clock -e hhmm 1405; demo notifications -e visible false
  adb shell am start -W -n $PKG/.DemoActivity >/dev/null
  sleep 6
  # Opened again, so the chosen language is surely applied everywhere.
  adb shell cmd uimode night no >/dev/null; start_app
  shot 01_today
  # The same day at night: the app follows the phone, in the Hutsul theme
  adb shell cmd uimode night yes >/dev/null; start_app
  shot 02_today_night
  adb shell cmd uimode night no >/dev/null; start_app
  # The meals of today, then adding to breakfast (its "Add" is the first one left on screen)
  to_top "$BREAKFAST"; shot 03_today_meals
  tap "$ADD"; sleep 2; no_keyboard; shot 04_add_food
  adb shell input keyevent KEYCODE_BACK; sleep 2; start_app
  tap "$HISTORY"; sleep 3; shot 05_history
  up; shot 06_history_charts
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap "$WEIGHT"; shot 07_weight
  tap "$OPTIONS"; shot 10_options
  to_top "$DAY_THEME"; shot 08_themes
  tap "$FOODS"; shot 09_foods
}

s() { grep -o "name=\"$2\">[^<]*" "$ROOT/app/src/main/res/$1/strings.xml" | cut -d'>' -f2; }

run uk-UA uk Вага Продукти "$(s values-uk history_title)" "$(s values-uk tab_options)" "$(s values-uk day_theme)" "$(s values-uk meal_breakfast)" "$(s values-uk add)"
run en-US en-US Weight Foods "$(s values history_title)" "$(s values tab_options)" "$(s values day_theme)" "$(s values meal_breakfast)" "$(s values add)"
