#!/usr/bin/env bash
# Copy pictures to the launcher's wallpaper folder on the projector.
#   tools/push-wallpapers.sh [-s SERIAL] pic.jpg [pic2.png ...]
# The launcher's "My wallpaper" theme and the screensaver show what is in that folder.
# Pass -c to clear the folder first. jpg/jpeg/png/webp only; keep them at or near 1920x1080,
# the box has 1 GB of RAM and decodes every picture itself.
set -euo pipefail

SERIAL="${PROJECTOR_SERIAL:-${ANDROID_SERIAL:-}}"   # empty: the only device adb sees
CLEAR=0
while getopts "s:c" opt; do
  case $opt in
    s) SERIAL="$OPTARG" ;;
    c) CLEAR=1 ;;
    *) exit 2 ;;
  esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] || { echo "usage: $0 [-s SERIAL] [-c] picture..." >&2; exit 2; }

ADB() { if [ -n "$SERIAL" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }

DIR=/sdcard/Android/data/com.cleanbeam.launcher/files/wallpapers
ADB shell mkdir -p "$DIR"
[ $CLEAR = 1 ] && ADB shell "rm -f $DIR/*"
for f in "$@"; do
  case "${f##*.}" in
    jpg|jpeg|png|webp|JPG|JPEG|PNG|WEBP) ADB push "$f" "$DIR/" ;;
    *) echo "skip $f: not jpg/png/webp" >&2 ;;
  esac
done
# A folder that adb created (before the launcher ever ran) belongs to shell with mode 2770, and the
# launcher cannot list it: "My wallpaper" then stays a flat colour. Let others read it, and keep the
# setgid bit (plain 777 drops it, and the next adb push fails with "remote fchown failed").
ADB shell "chmod 2775 $DIR; chmod a+r $DIR/*" || true
ADB shell ls -l "$DIR"
