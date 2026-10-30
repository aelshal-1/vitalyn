#!/usr/bin/env bash
# Emulator smoke test: installs the debug APK, opens every tab and the main pushed screens,
# saves a screenshot of each, and fails if the app crashed. Run from android/ with a booted emulator.
set -u
PKG=com.elshal.vitalyn.debug
OUT=smoke
mkdir -p "$OUT"
crashed=0

shot() {
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "📸 $1"
}

check_crash() {
  if adb logcat -b crash -d | grep -q "$PKG"; then
    echo "::error::Crash after step: $1"
    adb logcat -b crash -d > "$OUT/crash-$1.txt"
    crashed=1
  fi
}

# Taps the first on-screen node whose text or content-desc contains $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  local bounds
  bounds=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    if needle in (node.get("text") or "") or needle in (node.get("content-desc") or ""):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$bounds" ]; then
    echo "::warning::Couldn't find '$1' on screen"
    return 1
  fi
  adb shell input tap $bounds
}

launch() {
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/com.elshal.vitalyn.MainActivity > /dev/null
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

# 1. First launch: onboarding
launch
shot 01-onboarding 6
check_crash onboarding

# 2. Seed a profile, a meal and an InBody scan, then open the main app
adb shell am force-stop $PKG
SEED='{"profiles":[{"name":"Ahmed","birthYear":1990,"heightCm":178,"startWeightKg":96,"goalWeightKg":82,"conditionsRaw":["type2Diabetes"]}],"meals":[{"slotRaw":"lunch","titleAr":"كشري","titleEn":"Koshary","items":[{"nameAr":"كشري","nameEn":"Koshary","grams":350,"kcal":560,"proteinG":18,"carbsG":95,"fatG":12}]}],"scans":[{"weightKg":95.2,"skeletalMuscleKg":36.1,"bodyFatPercent":31.5,"visceralFatLevel":12,"bmrKcal":1820,"rightArmKg":3.6,"leftArmKg":3.5,"trunkKg":28.4,"rightLegKg":9.8,"leftLegKg":9.7,"summaryAr":"ملخص","summaryEn":"Summary"}]}'
adb shell "run-as $PKG sh -c 'mkdir -p files/store && cat > files/store/vitalyn.json'" <<< "$SEED"
launch
shot 02-today 6
check_crash today

tap "Food" && shot 03-food
check_crash food
tap "Products" && shot 04-products
tap "Weekly plan" && shot 05-plan
check_crash plan
tap "Coach" && shot 06-coach
check_crash coach
tap "Me" && shot 07-me
check_crash me
tap "Progress" && shot 08-progress
check_crash progress
tap "My body" && shot 09-body 6
check_crash body
adb shell input keyevent KEYCODE_BACK
tap "Body scans" && shot 10-inbody
check_crash inbody
adb shell input keyevent KEYCODE_BACK
tap "Activity" && shot 11-activity
check_crash activity
adb shell input keyevent KEYCODE_BACK
tap "Achievements" && shot 12-achievements
check_crash achievements
adb shell input keyevent KEYCODE_BACK
tap "Today" && shot 13-today-again
check_crash today-again

adb logcat -d > "$OUT/logcat.txt"
exit $crashed
