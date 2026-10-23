#!/usr/bin/env bash
# Copy pictures to the launcher's wallpaper folder on the projector.
#   tools/push-wallpapers.sh [-s SERIAL] pic.jpg [pic2.png ...]
# The launcher's "My wallpaper" theme and the screensaver show what is in that folder.
# Pass -c to clear the folder first. jpg/jpeg/png/webp only; keep them at or near 1920x1080,
# the box has 1 GB of RAM and decodes every picture itself.
set -euo pipefail

SERIAL="${PROJECTOR_SERIAL:-192.168.10.58:3333}"
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

DIR=/sdcard/Android/data/com.cleanyourprojector.launcher/files/wallpapers
adb -s "$SERIAL" shell mkdir -p "$DIR"
[ $CLEAR = 1 ] && adb -s "$SERIAL" shell "rm -f $DIR/*"
for f in "$@"; do
  case "${f##*.}" in
    jpg|jpeg|png|webp|JPG|JPEG|PNG|WEBP) adb -s "$SERIAL" push "$f" "$DIR/" ;;
    *) echo "skip $f: not jpg/png/webp" >&2 ;;
  esac
done
adb -s "$SERIAL" shell ls -l "$DIR"
