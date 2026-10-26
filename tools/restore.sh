#!/usr/bin/env bash
# Undo tools/debloat.sh from the backup it wrote: re-enable what it disabled, put Home and the
# screensaver back. With no argument it uses the newest folder under backups/.
#
#   tools/restore.sh                         # newest backup
#   tools/restore.sh backups/<device>-<date>  # a specific one
#   tools/restore.sh --list models/<model>/debloat.txt   # no backup: re-enable every package in a list
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SH() { adb shell "$@" | tr -d '\r'; }
adb get-state >/dev/null 2>&1 || { echo "no device: run 'adb connect <ip>:5555' (or :3333) first, or set ANDROID_SERIAL" >&2; exit 1; }

if [ "${1:-}" = "--list" ]; then
  LIST="${2:?usage: tools/restore.sh --list <debloat list>}"
  for pkg in $(LC_ALL=C awk '{sub(/\r$/,""); sub(/#.*/,""); if ($1!="") print $1}' "$LIST"); do
    SH pm enable "$pkg" >/dev/null 2>&1 && echo "enabled $pkg" || echo "skip    $pkg"
  done
  exit 0
fi

BACKUP="${1:-$(ls -1d "$ROOT"/backups/*/ 2>/dev/null | sort | tail -n 1)}"
BACKUP="${BACKUP%/}"
[ -n "$BACKUP" ] && [ -d "$BACKUP" ] || { echo "no backup found under backups/ (or pass one, or use --list <file>)" >&2; exit 1; }
echo "restoring from $BACKUP"

while read -r pkg; do
  [ -n "$pkg" ] || continue
  SH pm enable "$pkg" >/dev/null 2>&1 && echo "enabled $pkg" || echo "skip    $pkg"
done < "$BACKUP/disabled-by-us.txt"

HOME_BEFORE="$(tr -d '[:space:]' < "$BACKUP/home-before.txt")"
case "$HOME_BEFORE" in
  */*) SH cmd package set-home-activity "$HOME_BEFORE" >/dev/null && echo "Home -> $HOME_BEFORE"
       # EGate/Jixin boards pick Home from this property (models/egate-atom-3x/README.md).
       if [ -n "$(SH getprop persist.jx.launcher.highpriority)" ]; then
         SH setprop persist.jx.launcher.highpriority "${HOME_BEFORE%%/*}"
       fi ;;
esac

SS_BEFORE="$(tr -d '[:space:]' < "$BACKUP/screensaver-before.txt")"
if [ -n "$SS_BEFORE" ] && [ "$SS_BEFORE" != "null" ]; then
  SH settings put secure screensaver_components "$SS_BEFORE" && echo "screensaver -> $SS_BEFORE"
fi
echo "restore complete. Clean Beam apps stay installed: adb uninstall com.cleanbeam.launcher (and com.cleanbeam.remote) to remove them."
