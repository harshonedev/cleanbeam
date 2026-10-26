#!/usr/bin/env bash
# Back up, then disable every package in a debloat list (one package per line, # comments allowed).
# Packages are disabled for user 0 with `pm disable-user`, never uninstalled. Undo with tools/restore.sh.
#
#   tools/debloat.sh models/<your-model>/debloat.txt
#   ANDROID_SERIAL=192.168.1.50:5555 tools/debloat.sh models/<your-model>/debloat.txt   # several devices
#   DRY_RUN=1 tools/debloat.sh <list>      # print what would happen, change nothing
set -euo pipefail
LIST="${1:?usage: tools/debloat.sh <debloat list>  (e.g. models/egate-atom-3x/debloat.txt)}"
[ -r "$LIST" ] || { echo "cannot read the list: $LIST" >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SH() { adb shell "$@" | tr -d '\r'; }

# Windows editors save a BOM and CRLF line ends; strip both, then comments and blank lines.
PKGS=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); sub(/#.*/,""); if ($1!="") print $1}' "$LIST")
[ -n "$PKGS" ] || { echo "refusing $LIST: no package names in it (every line is a comment or blank)" >&2; exit 1; }

# The name ends up in a shell command on the device, so take bare package names only.
BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
[ -z "$BAD" ] || { echo "refusing $LIST, not a package name:" >&2; printf '  %s\n' $BAD >&2; exit 1; }

# Core Android: with one of these off, the device may never boot again. Same rule as cleanyourtv's
# tools/disable.sh (its issue #884: a Mi TV Stick bricked by a list that took the permission
# controller and package installer). No switch overrides this.
CORE_RE='^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.android\.vending|com\.google\.android\.gms|com\.google\.android\.gsf|com\.android\.inputmethod.*|com\.google\.android\.inputmethod.*)$'
CORE=$(printf '%s\n' "$PKGS" | grep -E "$CORE_RE" || true)
if [ -n "$CORE" ]; then
  echo "refusing $LIST: it disables core Android packages, and the device may not boot (or type) again:" >&2
  printf '  %s\n' $CORE >&2
  echo "Take those lines out and run it again. Nothing was changed." >&2
  exit 1
fi

adb get-state >/dev/null 2>&1 || { echo "no device: run 'adb connect <ip>:5555' (or :3333) first, or set ANDROID_SERIAL" >&2; exit 1; }
MODEL="$(SH getprop ro.product.model)"
HOME_PKG="$(SH cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | cut -d/ -f1)"

# Back up first: what is installed, what was already off, Home and the screensaver.
STAMP="$(date +%Y%m%d-%H%M%S)"
SAFE="$(printf '%s' "${ANDROID_SERIAL:-$MODEL}" | tr -c 'A-Za-z0-9._-' '_')"
BACKUP="$ROOT/backups/$SAFE-$STAMP"
if [ -z "${DRY_RUN:-}" ]; then
  mkdir -p "$BACKUP"
  SH pm list packages | sed 's/^package://' | sort > "$BACKUP/packages.txt"
  SH pm list packages -d | sed 's/^package://' | sort > "$BACKUP/disabled-before.txt"
  SH cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 > "$BACKUP/home-before.txt"
  SH settings get secure screensaver_components > "$BACKUP/screensaver-before.txt"
  SH getprop > "$BACKUP/getprop.txt"
  : > "$BACKUP/disabled-by-us.txt"
  echo "backup: $BACKUP"
fi

INSTALLED="$(SH pm list packages | sed 's/^package://')"
ALREADY_OFF="$(SH pm list packages -d | sed 's/^package://')"
for pkg in $PKGS; do
  if ! printf '%s\n' "$INSTALLED" | grep -qxF "$pkg"; then echo "SKIP  $pkg (not installed)"; continue; fi
  # Left out of disabled-by-us.txt, so a restore does not switch on what was off before we came.
  if printf '%s\n' "$ALREADY_OFF" | grep -qxF "$pkg"; then echo "SKIP  $pkg (already disabled)"; continue; fi
  # Disabling the app that answers Home leaves a black screen with nothing to go back to.
  if [ "$pkg" = "$HOME_PKG" ] && [ -z "${FORCE_HOME:-}" ]; then
    echo "SKIP  $pkg (it is the Home screen right now; make Clean Beam Home first, or FORCE_HOME=1)"; continue
  fi
  if [ -n "${DRY_RUN:-}" ]; then echo "WOULD $pkg"; continue; fi
  out="$(SH pm disable-user --user 0 "$pkg" 2>&1 || true)"
  if printf '%s' "$out" | grep -q 'disabled-user'; then
    echo "OK    $pkg"; echo "$pkg" >> "$BACKUP/disabled-by-us.txt"
  else
    echo "WARN  $pkg -> $out"
  fi
done
[ -n "${DRY_RUN:-}" ] || echo "done. Undo everything with: tools/restore.sh $BACKUP"
