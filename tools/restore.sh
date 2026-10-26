#!/usr/bin/env bash
# Revert all customizations using a snapshot dir (default: ~/Atom3X-backup/customize-latest).
# Re-enables disabled bloat, restores original HOME launcher and screensaver.
set -euo pipefail
SNAP="${1:-$HOME/Atom3X-backup/customize-latest}"
SER="${ADB_SERIAL:-}"
ADB() { if [ -n "$SER" ]; then adb -s "$SER" "$@"; else adb "$@"; fi; }

[ -d "$SNAP" ] || { echo "snapshot not found: $SNAP"; exit 1; }
echo "restoring from $SNAP"

HERE="$(cd "$(dirname "$0")" && pwd)"
# 1. re-enable every package in debloat.txt
grep -vE '^\s*#|^\s*$' "$HERE/debloat.txt" | while read -r pkg; do
  pkg="$(echo "$pkg" | tr -d '[:space:]')"; [ -z "$pkg" ] && continue
  ADB shell pm enable "$pkg" >/dev/null 2>&1 && echo "enabled $pkg" || echo "skip $pkg"
done

# 2. restore original HOME
HOME_BEFORE="$(tr -d '[:space:]' < "$SNAP/home-before.txt")"
[ -n "$HOME_BEFORE" ] && ADB shell cmd package set-home-activity "$HOME_BEFORE" && echo "HOME -> $HOME_BEFORE"

# 3. restore original screensaver
SS_BEFORE="$(tr -d '[:space:]' < "$SNAP/screensaver-before.txt")"
if [ -n "$SS_BEFORE" ] && [ "$SS_BEFORE" != "null" ]; then
  ADB shell settings put secure screensaver_components "$SS_BEFORE" && echo "screensaver -> $SS_BEFORE"
fi
echo "restore complete. (com.cleanbeam.launcher left installed; 'adb uninstall com.cleanbeam.launcher' to remove.)"
