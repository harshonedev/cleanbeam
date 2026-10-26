# Rules for AI agents working on a real projector

Claude Code, Codex, Gemini CLI and other agents: read this before running anything on a device.
The prompts in [PROMPTS.md](PROMPTS.md) point here.

## Use this repo's tools

- Clone the repo and work from its root. Use `tools/scan.sh`, `tools/debloat.sh`, `tools/restore.sh`,
  `tools/push-wallpapers.sh` and `tools/install-remote.sh` instead of writing your own: the backup,
  the core-package guard and the Home guard live there.
- If adb sees more than one device, set `ANDROID_SERIAL=<ip>:<port>` for every command.

## Never

- **Never uninstall** a system package (`pm uninstall`, `pm uninstall --user 0`). Disable only:
  `pm disable-user --user 0`.
- **Never disable core Android:** `android`, `com.android.systemui`, `com.android.shell`,
  `com.android.phone`, `com.android.settings`, `com.android.tv.settings`, the package installer,
  permission controller, `ext.services`, `ext.shared`, `modulemetadata`,
  `com.google.android.overlay.modules*`, `com.android.providers.*`. The device may never boot again.
- **Never disable** Play Store / Play Services / GSF, the keyboard, the projector's own controls
  (focus, keystone, picture, vendor settings app), the TV input / HDMI layer (`*tvinput*`,
  `*.tv.settings`, `com.droidlogic.*`, `com.softwinner.*` input services), Bluetooth and remote-control
  services, or **whatever is Home right now**.
- **Never** `adb root`, flash, remount `/system`, or touch partitions or the bootloader.
- **Never** commit a TMDB token, Wi-Fi name, IP address, serial number or pairing token. Blur Wi-Fi
  names in screenshots.

## Always

- **Back up before changing anything.** `tools/debloat.sh` does this. If you run `pm` by hand, save
  `pm list packages`, `pm list packages -d`, the Home activity and `settings get secure
  screensaver_components` first.
- **Ask the user** before disabling anything they might use: streaming apps, screen mirroring
  (eShare, Miracast, cast), voice assistant, the OTA / update service, file managers and media
  players. Use a checklist question.
- When a package name says nothing, look at it with `dumpsys package <pkg>` (activities, services,
  permissions) before deciding. A package asking for `TV_INPUT_HARDWARE`, `WRITE_SECURE_SETTINGS`
  or `INJECT_EVENTS` is firmware plumbing: leave it unless you're sure.
- **Install the Clean Beam launcher and make it Home *before* disabling the stock launcher**. Better
  still, never disable the stock launcher at all: it's the fallback.
- Run `pm` commands **one per shell call**. Chained commands can stop silently after the first.
  Check results with `pm list packages -d`, not the exit code.
- **Verify with screenshots** from the device: `adb exec-out screencap -p > shot.png`.
- After debloating, **reboot and check** HDMI input, focus, keystone, Wi-Fi, Bluetooth, the IR
  remote and sound. If anything broke, `tools/restore.sh` and re-apply in smaller batches.
- Network ADB on these boxes is often unauthenticated. Tell the user, and offer to switch it off at
  the end if they won't use the phone remote.

## Device facts worth knowing

- Network ADB is usually on **5555** or **3333**. Wireless debugging (`adb pair`) is the fallback.
- Home on some firmware is chosen by a vendor property, not `set-home-activity`
  (EGate/Jixin: `persist.jx.launcher.highpriority`). Check `getprop | grep -i launcher`.
- `adb install -r` of the launcher can reset Home. Re-set it after every reinstall.
- Some firmware resets `screensaver_components` at boot. Grant the launcher
  `WRITE_SECURE_SETTINGS` so it re-asserts its own.
- A projector's DHCP IP often changes after a reboot. `adb mdns services` finds it.
- `adb install` on Android 14 can hang waiting for on-screen confirmation. Push to
  `/data/local/tmp` and `pm install -r` instead.

## Contributing back

A new projector's result belongs in `models/<brand>-<model>/` (`debloat.txt` with a comment per
group and a "kept on purpose" header, `device.json`, `README.md`). See [CONTRIBUTING.md](CONTRIBUTING.md).
Only list what was actually tested on hardware, and mark the rest with `# untested:`.
