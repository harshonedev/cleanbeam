#!/usr/bin/env bash
# Reversibly disable bloat packages listed in debloat.txt.
# Usage: ADB_SERIAL=<serial> ./debloat.sh   (or it auto-picks the only device)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LIST="$HERE/debloat.txt"
SER="${ADB_SERIAL:-}"
ADB() { if [ -n "$SER" ]; then adb -s "$SER" "$@"; else adb "$@"; fi; }

grep -vE '^\s*#|^\s*$' "$LIST" | while read -r pkg; do
  pkg="$(echo "$pkg" | tr -d '[:space:]')"
  [ -z "$pkg" ] && continue
  if ! ADB shell pm list packages | grep -q "^package:$pkg$"; then
    echo "SKIP  $pkg (not installed)"; continue
  fi
  out="$(ADB shell pm disable-user --user 0 "$pkg" 2>&1 || true)"
  if echo "$out" | grep -q 'new state: disabled-user'; then
    echo "OK    $pkg disabled"
  else
    echo "WARN  $pkg -> $out"
  fi
done
