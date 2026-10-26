#!/usr/bin/env bash
# Read-only report of a projector, the starting point for a new models/<model>/ folder: device
# properties, Home, screensaver, network ADB port, and every package with its APK path, sorted into
# system and user-installed. Changes nothing on the device.
#
#   tools/scan.sh                 # prints the report and saves it to scans/<model>-<date>.txt
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SH() { adb shell "$@" | tr -d '\r'; }
adb get-state >/dev/null 2>&1 || { echo "no device: run 'adb connect <ip>:5555' (or :3333) first, or set ANDROID_SERIAL" >&2; exit 1; }

MODEL="$(SH getprop ro.product.model)"
OUT="$ROOT/scans/$(printf '%s' "$MODEL" | tr -c 'A-Za-z0-9._-' '_')-$(date +%Y%m%d).txt"
mkdir -p "$ROOT/scans"
{
  echo "# Clean Beam scan, $(date '+%Y-%m-%d %H:%M')"
  for p in ro.product.brand ro.product.manufacturer ro.product.model ro.product.device ro.board.platform \
           ro.build.version.release ro.build.version.sdk ro.build.display.id ro.adb.secure \
           service.adb.tcp.port persist.adb.tcp.port; do
    printf '%-28s %s\n' "$p" "$(SH getprop "$p")"
  done
  echo "screen                       $(SH wm size | tail -n 1)"
  echo "home                         $(SH cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
  echo "screensaver                  $(SH settings get secure screensaver_components)"
  echo
  echo "## Apps that can be Home"
  SH cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / || true
  echo
  echo "## System packages, enabled (package  apk path)"
  SH pm list packages -s -e -f | sed -E 's/^package:(.*)=([^=]+)$/\2  \1/' | sort
  echo
  echo "## System packages, already disabled"
  SH pm list packages -s -d | sed 's/^package://' | sort
  echo
  echo "## User-installed packages"
  SH pm list packages -3 -f | sed -E 's/^package:(.*)=([^=]+)$/\2  \1/' | sort
} | tee "$OUT"
echo
echo "saved: $OUT"
