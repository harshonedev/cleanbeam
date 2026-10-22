#!/usr/bin/env bash
# Build (optional), install and start the EGate Remote receiver on the projector.
#   tools/install-remote.sh [--build]
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SERIAL:=192.168.10.52:3333}"
export ANDROID_SERIAL
if [[ "${1:-}" == "--build" ]]; then (cd remote/receiver && ./gradlew assembleDebug -q); fi
APK=remote/receiver/app/build/outputs/apk/debug/app-debug.apk
PKG=io.egate.remote
adb install -r "$APK"
adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT || true
adb shell pm grant $PKG android.permission.BLUETOOTH_ADVERTISE || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell am start-foreground-service $PKG/.RemoteService
adb shell am start -n $PKG/.PairingActivity
echo "installed. token:"; adb shell "cat /data/data/$PKG/shared_prefs/pairing.xml 2>/dev/null | grep -o 'token\">[^<]*' | cut -d'>' -f2" || true
