#!/usr/bin/env bash
# Installs a release build (R8 on) on the running emulator, signed with a throwaway key, goes through the
# first-start dialog and every tab, then starts it again: any crash fails the run. R8 can remove or rename
# something that only fails when the app runs, and the unit tests use the debug build, so this is the check.
#   bash scripts/screenshots/smoke_release.sh app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
set -euo pipefail
APK="$1"
PKG=ua.tarilka
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"

keytool -genkeypair -keystore "$TMP/smoke.jks" -storepass smokeonly -keypass smokeonly -alias smoke \
  -keyalg RSA -keysize 2048 -validity 1 -dname "CN=smoke test" >/dev/null 2>&1
"$BT/zipalign" -f -p 4 "$APK" "$TMP/aligned.apk"
"$BT/apksigner" sign --ks "$TMP/smoke.jks" --ks-pass pass:smokeonly --out "$TMP/smoke.apk" "$TMP/aligned.apk"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$TMP/smoke.apk" >/dev/null
adb logcat -c

tap() {
  for i in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$TMP/ui.xml" >/dev/null 2>&1 || true
    if XY=$(python3 "$HERE/find.py" "$TMP/ui.xml" "$1"); then adb shell input tap $XY; sleep 2; return 0; fi
    sleep 2
  done
  echo "::error::'$1' not found on screen (release build)"; return 1
}
crashed() { adb logcat -d -b crash | grep -q "$PKG"; }
alive() { [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; }

check() {
  sleep 2
  if crashed || ! alive; then
    echo "::error::the release build stopped: $1"
    adb logcat -d -b crash | tail -60
    exit 1
  fi
  echo "release ok: $1"
}

adb shell am start -W -n "$PKG/.ui.MainActivity" >/dev/null; sleep 6
check "first start"
tap "Skip"; tap "I am 18 or older"
check "first-start dialog"
for t in Weight Foods Options Today; do tap "$t"; check "tab $t"; done
adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/.ui.MainActivity" >/dev/null; sleep 6
check "second start (the database opens again)"
adb uninstall "$PKG" >/dev/null 2>&1 || true
