#!/usr/bin/env python3
"""Checks the merged manifest inside a built APK (aapt2 dump xmltree): no cloud backup or device transfer,
no plain-HTTP traffic, not debuggable, and only the components on the list below open to other apps.

  python3 .github/check-manifest.py <aapt2> <apk>
"""
import re
import subprocess
import sys

# Components other apps may start, and why. Anything else exported fails the build.
EXPORTED_OK = {
    # The launcher icon, and Health Connect asking why Tarilka wants access (Android 13 and lower)
    "ua.tarilka.ui.MainActivity",
    # The same Health Connect question on Android 14+; only the system may send it (START_VIEW_PERMISSION_USAGE)
    "ua.tarilka.ViewPermissionUsageActivity",
}
# An exported component is fine anyway when only the system can reach it: a permission no ordinary app gets.
SYSTEM_ONLY = {"android.permission.DUMP", "android.permission.START_VIEW_PERMISSION_USAGE", "android.permission.BIND_JOB_SERVICE"}
COMPONENTS = {"activity", "activity-alias", "service", "receiver", "provider"}

aapt2, apk = sys.argv[1], sys.argv[2]
out = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                     check=True, capture_output=True, text=True).stdout

# Elements with their depth and attributes, from aapt2's indented listing
elements = []
for line in out.splitlines():
    m = re.match(r"^(\s*)E: ([\w-]+)", line)
    if m:
        elements.append({"depth": len(m.group(1)), "tag": m.group(2), "attrs": {}, "children": []})
        continue
    a = re.match(r'^\s*A: (?:http://schemas.android.com/apk/res/android:)?(\w+)(?:\(0x[0-9a-f]+\))?=(.*)$', line)
    if a and elements:
        value = a.group(2)
        value = value.split(" (Raw:")[0].strip().strip('"')
        elements[-1]["attrs"][a.group(1)] = value

def has_child(i, tag):
    """An element directly or further inside element i has this tag (e.g. an intent-filter)."""
    d = elements[i]["depth"]
    for e in elements[i + 1:]:
        if e["depth"] <= d:
            return False
        if e["tag"] == tag:
            return True
    return False

problems = []
app = next((e for e in elements if e["tag"] == "application"), None)
if app is None:
    problems.append("no <application> in the manifest")
else:
    a = app["attrs"]
    if a.get("allowBackup", "").lower() not in ("false", "0", "0x0"):
        problems.append(f"allowBackup must be false (is {a.get('allowBackup', 'not set')})")
    if "dataExtractionRules" not in a:
        problems.append("dataExtractionRules is missing (cloud backup and device transfer must be excluded)")
    if a.get("usesCleartextTraffic", "").lower() in ("true", "1", "0xffffffff"):
        problems.append("usesCleartextTraffic is on")
    if a.get("debuggable", "").lower() in ("true", "1", "0xffffffff"):
        problems.append("the app is debuggable")

for i, e in enumerate(elements):
    if e["tag"] not in COMPONENTS:
        continue
    name = e["attrs"].get("name", "?")
    exported = e["attrs"].get("exported")
    is_exported = exported in ("true", "1", "0xffffffff") or (exported is None and has_child(i, "intent-filter"))
    if not is_exported:
        continue
    if name in EXPORTED_OK or e["attrs"].get("permission") in SYSTEM_ONLY:
        print(f"exported (allowed): {e['tag']} {name} {e['attrs'].get('permission', '')}")
        continue
    problems.append(f"{e['tag']} {name} is open to other apps (exported) and is not on the list")

for p in problems:
    print(f"::error::{p}")
if problems:
    sys.exit(1)
print("Manifest OK")
