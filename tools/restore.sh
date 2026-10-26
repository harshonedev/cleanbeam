#!/usr/bin/env bash
# Undo tools/debloat.sh from the backup it wrote: re-enable what it disabled, put Home and the
# screensaver back. With no argument it uses the newest folder under backups/.
#
#   tools/restore.sh                         # newest backup
#   tools/restore.sh backups/<device>-<date>  # a specific one
#   tools/restore.sh --list models/<model>/debloat.txt   # no backup: re-enable every package in a list
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/tools/lib.sh"
SH() { adb shell "$@" | tr -d '\r'; }
enable_all() {
  for pkg in $1; do
    SH pm enable "$pkg" >/dev/null 2>&1 && echo "enabled $pkg" || echo "skip    $pkg"
  done
}

# Read and check everything before the first command reaches the device.
if [ "${1:-}" = "--list" ]; then
  LIST="${2:?usage: tools/restore.sh --list <debloat list>}"
  [ -r "$LIST" ] || { echo "cannot read the list: $LIST" >&2; exit 1; }
  PKGS="$(read_list "$LIST")"
  require_pkgs "$LIST" "$PKGS"
  adb get-state >/dev/null 2>&1 || { echo "no device: run 'adb connect <ip>:5555' (or :3333) first, or set ANDROID_SERIAL" >&2; exit 1; }
  enable_all "$PKGS"
  exit 0
fi

BACKUP="${1:-$(ls -1d "$ROOT"/backups/*/ 2>/dev/null | sort | tail -n 1)}"
BACKUP="${BACKUP%/}"
[ -n "$BACKUP" ] && [ -d "$BACKUP" ] || { echo "no backup found under backups/ (or pass one, or use --list <file>)" >&2; exit 1; }
PKGS="$(read_list "$BACKUP/disabled-by-us.txt")"
require_pkgs "$BACKUP/disabled-by-us.txt" "$PKGS"
HOME_BEFORE="$(tr -d '[:space:]' < "$BACKUP/home-before.txt")"
is_component "$HOME_BEFORE" || HOME_BEFORE=""
# Usually one component; some firmware stores a comma-separated list.
SS_BEFORE="$(tr -d '[:space:]' < "$BACKUP/screensaver-before.txt")"
for c in $(printf '%s' "$SS_BEFORE" | tr ',' ' '); do
  is_component "$c" || { echo "ignoring screensaver-before.txt: not a component name" >&2; SS_BEFORE=""; break; }
done

adb get-state >/dev/null 2>&1 || { echo "no device: run 'adb connect <ip>:5555' (or :3333) first, or set ANDROID_SERIAL" >&2; exit 1; }
echo "restoring from $BACKUP"
enable_all "$PKGS"

if [ -n "$HOME_BEFORE" ]; then
  SH cmd package set-home-activity "$HOME_BEFORE" >/dev/null && echo "Home -> $HOME_BEFORE"
  # EGate/Jixin boards pick Home from this property (models/egate-atom-3x/README.md).
  if [ -n "$(SH getprop persist.jx.launcher.highpriority)" ]; then
    SH setprop persist.jx.launcher.highpriority "${HOME_BEFORE%%/*}"
  fi
fi

if [ -n "$SS_BEFORE" ]; then
  SH settings put secure screensaver_components "$SS_BEFORE" && echo "screensaver -> $SS_BEFORE"
fi
echo "restore complete. Clean Beam apps stay installed: adb uninstall com.cleanbeam.launcher (and com.cleanbeam.remote) to remove them."
