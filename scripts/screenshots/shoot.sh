#!/usr/bin/env bash
# Takes the store screenshots on a running emulator: installs the debug APK, loads the invented demo diary
# (DemoActivity), and photographs the eight store screens in Ukrainian and in English into
# fastlane/.../phoneScreenshots, in the order people see them: Today, adding food, foods, history and charts,
# weight, Today at night (the default Beeswax and Hutsul themes), the themes, About.
# Then, for review only (review/, never in the store): a narrow phone with the largest font, and a tablet.
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
to_top() {  # label [target y]
  local TARGET="${2:-380}"
  for i in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1 || true
    if XY=$(python3 "$HERE/find.py" "$TMP/ui.xml" "$1"); then
      Y=${XY#* }
      # Found low on the screen: drag it up to the target (never down, a drag from the top edge opens the shade)
      if [ "$Y" -gt $((TARGET + 40)) ]; then adb shell input swipe 540 "$Y" 540 "$TARGET" 900; fi
      sleep 2; return 0
    fi
    up
  done
  # What the screen showed, for the person reading the log
  adb exec-out screencap -p > "$OUT/missing.png" || true
  echo "::error::'$1' not found while scrolling"; return 1
}
# The keyboard, if a search box opened it, is not part of the picture.
no_keyboard() { adb shell dumpsys input_method | grep -q 'mInputShown=true' && adb shell input keyevent KEYCODE_BACK; sleep 1; return 0; }
start_app() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.ui.MainActivity >/dev/null; sleep 6; }

run() {
  local LOCALE="$1"; LANG_DIR="$2"; local WEIGHT="$3" FOODS="$4" HISTORY="$5" OPTIONS="$6" DAY_THEME="$7" BREAKFAST="$8" ADD="$9" ABOUT="${10}" TODAY="${11}"
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
  shot 01_home
  # Adding to breakfast (its "Add" is the first one on screen once breakfast is near the top)
  to_top "$BREAKFAST"; tap "$ADD"; sleep 2; no_keyboard; shot 02_add
  adb shell input keyevent KEYCODE_BACK; sleep 2; start_app
  tap "$FOODS"; shot 03_foods
  tap "$TODAY"; tap "$HISTORY"; sleep 3; adb shell input swipe 540 1900 540 1300 600; sleep 2; shot 04_history
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap "$WEIGHT"; shot 05_weight
  # The same day at night: the app follows the phone, in the Hutsul theme
  adb shell cmd uimode night yes >/dev/null; start_app
  shot 06_home_night
  adb shell cmd uimode night no >/dev/null; start_app
  # The whole "Appearance" section, its title just below the status bar; then About
  tap "$OPTIONS"; to_top "$DAY_THEME" 600; shot 07_themes
  to_top "$ABOUT" 380; shot 08_options
}

# For review: how the app holds up on a narrow phone with the largest font, and on a tablet.
review() {
  local TODAY="$1" OPTIONS="$2" HISTORY="$3"
  OUT="$ROOT/review"; rm -rf "$OUT"; mkdir -p "$OUT"
  adb shell settings put system font_scale 1.3; adb shell wm density 480   # about 360 dp wide
  start_app; shot narrow_big_font_home; to_top "$HISTORY" 1200 || true; tap "$HISTORY" || true; sleep 3; shot narrow_big_font_history
  adb shell input keyevent KEYCODE_BACK; sleep 2; tap "$OPTIONS"; to_top "$4" 600 || true; shot narrow_big_font_themes
  adb shell cmd uimode night yes >/dev/null; start_app; shot narrow_big_font_home_night
  adb shell cmd uimode night no >/dev/null
  adb shell settings put system font_scale 1.0; adb shell wm size 1600x2560; adb shell wm density 320   # a 10-inch tablet
  start_app; shot tablet_home; tap "$OPTIONS"; shot tablet_options
  adb shell wm size reset; adb shell wm density reset
}

s() { grep -o "name=\"$2\">[^<]*" "$ROOT/app/src/main/res/$1/strings.xml" | cut -d'>' -f2; }

run uk-UA uk Вага Продукти "$(s values-uk history_title)" "$(s values-uk tab_options)" "$(s values-uk day_theme)" "$(s values-uk meal_breakfast)" "$(s values-uk add)" "$(s values-uk about)" "$(s values-uk tab_today)"
run en-US en-US Weight Foods "$(s values history_title)" "$(s values tab_options)" "$(s values day_theme)" "$(s values meal_breakfast)" "$(s values add)" "$(s values about)" "$(s values tab_today)"
LANG_DIR=review review "$(s values tab_today)" "$(s values tab_options)" "$(s values history_title)" "$(s values day_theme)"
