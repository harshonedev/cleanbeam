# Projector launcher — Design

> Historical design for the first projector this was built on. The launcher now targets Android projectors in general; see `README.md`.

**Date:** 2026-10-23
**Device:** EGate Atom 3X, Android 13 / SDK 33, Amlogic txhd2, Leanback TV, armeabi-v7a, 1280x720 override, root ADB over Wi-Fi (`192.168.10.51:3333`).
**Objective:** Replace stock Jixin launcher and screensaver with custom apps; remove selected bloatware. All changes reversible. No flashing, no system remount, no partition changes.

## Intent / why
Stock Jixin launcher and screensaver are generic and carry ads/content push. User wants an own clean UI/UX for an entertainment projector, plus removal of unwanted preinstalled apps. Backups already complete (12 boot images + launcher/OTA APKs).

## Goals
1. Custom home launcher: minimal D-pad-first app grid, clock, settings + Wi-Fi shortcuts.
2. Custom screensaver (DreamService): clock/ambient.
3. Disable (reversible) chosen bloat/telemetry apps.
4. Keep device bootable and Jixin launcher installed as fallback throughout.

## Non-goals
- No custom ROM, no partition flashing, no bootloader/slot changes.
- No security hardening this round (user decision: entertainment-only box).
- No uninstalling system packages — disable only (`pm disable-user`), so re-enable is one command.

## Anti-goals (would be failure)
- Device boots to no home screen.
- Losing ADB access.
- Disabling a package the launcher or core TV stack depends on (droidlogic HALs, tvinput, settings).

## Decisions made
- **Language/UI:** Kotlin, AndroidX Leanback + custom Compose/Views. minSdk 28, compileSdk/targetSdk 34. Single APK, debuggable, signed with a local debug/release key.
- **Launcher style:** minimal grid (my default; user did not pick row/dashboard). Declares `HOME` + `LEANBACK_LAUNCHER` + `DEFAULT` categories. Enumerates installed launchable apps via `queryIntentActivities` for both LEANBACK_LAUNCHER and LAUNCHER.
- **Switch HOME:** `cmd package set-home-activity` (reversible; Jixin stays installed). Fallback documented: re-run with Jixin component.
- **Screensaver:** new DreamService, set via `settings put secure screensaver_components`. Revert: restore `com.jixin.screensaver/...DeskClockDreamService`.
- **Install method:** `adb install -r`; app granted needed perms. Network ADB stays on (user decision).

## Debloat list (reversible `pm disable-user --user 0`)
- `cm.aptoide.pt` (third-party store; Play Store already present)
- `com.community.newroom`, `com.community.mbox.tv` (Transsion ad/content push)
- `com.jixin.analytics` (telemetry)
- `com.jixin.apppush` (remote app install)
- `com.jixin.ota` (Baidu OTA SDK; stops forced updates)

Explicitly KEPT: all `com.droidlogic.*` HALs, `com.droidlogic.tvinput`, `com.jixin.settings5`, `com.jixin.dispatcher`, Play Store, streaming apps, eShare. Piracy-flavored apps left installed per user (not selected).

Each disable verified; any regression (no boot-to-home, missing core function) is rolled back with `pm enable`.

## Left to builder
- Exact launcher layout/theme details within "minimal grid".
- Screensaver visual (clock style).
- Whether to include a "disabled apps" toggle screen in the launcher later.

## Rollback summary
- Launcher: `cmd package set-home-activity com.jixin.launcher5/.MainActivity`
- Screensaver: `settings put secure screensaver_components com.jixin.screensaver/.DeskClockDreamService`
- Bloat: `pm enable <pkg>` for each disabled package.
- Our apps: `adb uninstall <our pkg>`.

---
## Build status (2026-10-23, verified across a reboot)
- Launcher `io.egate.launcher` built, installed, default HOME. Survives reboot (needs `persist.jx.launcher.highpriority=io.egate.launcher`).
- Screensaver = our ClockDreamService; requires `com.jixin.screensaver` disabled (its boot receiver reset the setting). Survives reboot.
- Debloat (7 disabled, reversible): aptoide, community.newroom, community.mbox.tv, jixin.analytics, jixin.apppush, jixin.ota, jixin.screensaver.
- `persist.adb.tcp.port=3333` set so network ADB auto-starts after reboot (user chose to keep network ADB on).
- Rollback: `tools/restore.sh` (uses ~/Atom3X-backup/customize-latest).
- Note: Wi-Fi client isolation broke ADB after reboot until the projector rejoined a non-isolated SSID. DHCP moved it .51 -> .52.
