# EGate Atom 3X

| | |
|---|---|
| Board | Amlogic txhd2, `t950s_be311` (shows as "Google t950s_be311") |
| Android | 13 |
| Firmware | Jixin (`com.jixin.*`) |
| Network ADB | port **3333** (5555 refuses) |
| Status | Tested: debloat, Full and Lite launcher, phone remote, screensaver |

![Before and after: the stock Jixin launcher, then Clean Beam](../../docs/images/before-after.jpg)

## Debloat

```sh
adb connect <projector-ip>:3333
tools/debloat.sh models/egate-atom-3x/debloat.txt
```

## Making Clean Beam the Home screen

This firmware ignores Android's normal default-Home choice. It reads a system property instead,
and the stock launcher writes its own name into that property whenever it runs. Set both:

```sh
adb shell cmd package set-home-activity com.cleanbeam.launcher/.LauncherActivity
adb shell setprop persist.jx.launcher.highpriority com.cleanbeam.launcher
```

The property survives a reboot. `adb install -r` of the launcher can reset Home to the stock
launcher: run both lines again after a reinstall. After an *uninstall* (switching between a
self-built and a release APK), the stock launcher runs as Home in the meantime and rewrites the
property a few seconds later. Set both lines **after** the new launcher is installed and on screen,
then check with `adb shell getprop persist.jx.launcher.highpriority`.

Back to stock: `adb shell setprop persist.jx.launcher.highpriority com.jixin.launcher5`.

## Screensaver

Two vendor apps reset the screensaver to their own on boot: `com.jixin.screensaver` (in the list
above) and `com.jixin.settings5` (the settings app, which must stay). Let the launcher put its own
clock back each time you return Home:

```sh
adb shell pm grant com.cleanbeam.launcher android.permission.WRITE_SECURE_SETTINGS
```

## Projector controls

Focus, keystone, brightness, projection mode and input source open the Jixin screens
(`jixin.intent.action.*`). Both the launcher's Quick settings and the phone remote find them on
their own.

## Quirks

- The IR remote's power key is keycode 135 (a vendor menu), not `KEYCODE_POWER`.
- Sleep is a full suspend: Wi-Fi and Bluetooth go off, so the phone remote cannot wake it.
- DHCP gives it a new IP after most reboots. `adb mdns services` finds it again.
- Network ADB is open with no authorisation (`ro.adb.secure=0`). Anyone on your Wi-Fi can reach
  it, so use a network you trust.
