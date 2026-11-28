#!/usr/bin/env bash
# Takes the Play Store phone screenshots on an emulator with made-up data (never on the owner's
# phone: its data is real). Run it only when something visible on these screens changed; a
# screenshot that comes out the same as the stored one (bar the clock) is kept as it is.
#
#   ./scripts/play-screenshots.sh
#
# Creates the AVD "Kladde_API36" (1080 x 2160: Play allows at most 2:1) on first use, boots it
# headless, and writes app/src/main/play/listings/{de-DE,en-US}/graphics/phone-screenshots/N.png,
# where Gradle Play Publisher picks them up (./gw publishListing).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
set -a
# shellcheck disable=SC1091
[ -f .env ] && . ./.env
set +a

# The system image lives in this SDK; the emulator has to look there.
IMAGE_SDK=${EMULATOR_SDK:-/opt/android-sdk}
AVD=Kladde_API36
AVD_DIR="$HOME/.android/avd/$AVD.avd"
SERIAL=emulator-5580
PACKAGE=de.steppicrew.kladde.debug
ACTIVITY=$PACKAGE/de.steppicrew.kladde.MainActivity

if [ ! -d "$AVD_DIR" ]; then
    mkdir -p "$AVD_DIR"
    cat > "$HOME/.android/avd/$AVD.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_DIR
path.rel=avd/$AVD.avd
target=android-36
EOF
    cat > "$AVD_DIR/config.ini" <<EOF
PlayStore.enabled=true
abi.type=x86_64
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2160
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=4096
image.sysdir.1=system-images/android-36/google_apis_playstore/x86_64/
tag.id=google_apis_playstore
target=android-36
EOF
fi

a() { adb -s "$SERIAL" "$@"; }

if ! adb devices | grep -q "^$SERIAL"; then
    ANDROID_SDK_ROOT="$IMAGE_SDK" ANDROID_HOME="$IMAGE_SDK" "$IMAGE_SDK/emulator/emulator" \
        -avd "$AVD" -port "${SERIAL#emulator-}" -no-window -no-audio -no-boot-anim -no-snapshot \
        >/dev/null 2>&1 &
    a wait-for-device
    until [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
fi

# A calm status bar: fixed clock, full battery, no notifications.
a shell settings put global sysui_demo_allowed 1
demo() { a shell am broadcast -a com.android.systemui.demo -p com.android.systemui -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0900
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false
a shell cmd alarm set-timezone Europe/Berlin >/dev/null 2>&1 || true

./gw -q assembleDebug
a install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

shot() { # $1 file; replaces it only if the screen really differs
    local new="$1.new.png"
    a exec-out screencap -p > "$new"
    # Pixels that differ (ImageMagick prints e.g. "1234 (0.0005)"); the clock alone stays far below.
    local changed
    changed=$(magick compare -metric AE -fuzz 2% "$1" "$new" null: 2>&1 | awk '{print int($1)}' || true)
    if [ -f "$1" ] && [ "${changed:-999999}" -lt 2000 ]; then
        rm "$new"
    else
        mv "$new" "$1"
        echo "  new: $1"
    fi
}

# Starts the app with a debug command and waits until its tab bar is on screen.
cmd() {
    a shell am start -S -n "$ACTIVITY" -e cmd "$1" >/dev/null
    for _ in $(seq 30); do
        # A failed dump leaves the old file behind, which may show the screen before.
        a shell rm -f /sdcard/ui.xml
        a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
        if a shell cat /sdcard/ui.xml 2>/dev/null | grep -q -E 'text="(Einträge|Entries)"'; then
            sleep 2
            return
        fi
        sleep 1
    done
    echo "  app didn't show up after $1" >&2
    exit 1
}

for listing in de-DE en-US; do
    lang=${listing%-*}
    out="app/src/main/play/listings/$listing/graphics/phone-screenshots"
    mkdir -p "$out"
    a shell pm clear "$PACKAGE" >/dev/null
    a shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    # As on a phone where the user allowed exact alarms: no hint card.
    a shell appops set "$PACKAGE" SCHEDULE_EXACT_ALARM allow
    a shell cmd locale set-app-locales "$PACKAGE" --user 0 --locales "$listing"
    cmd "demo:$lang"
    echo "$listing"
    cmd view:week; cmd tab:routines; shot "$out/1.png"
    cmd view:month; cmd tab:routines; shot "$out/2.png"
    cmd view:list; cmd tab:routines; shot "$out/3.png"
    cmd tab:entries; shot "$out/4.png"
done
