# 3. Debloat

Projector firmware is full of packages you never asked for: ad and "content" pushers, analytics,
remote app installers, forced-update services, third-party app stores, ad screensavers. Clean Beam
turns them off with `pm disable-user --user 0`:

- The app stops running and disappears from the app list.
- It is **not uninstalled**. `pm enable <package>` or `tools/restore.sh` brings it back.
- A factory reset brings everything back too.

## Your projector already has a list

Look in [`models/`](../../models/). If your projector is there:

```sh
tools/debloat.sh models/egate-atom-3x/debloat.txt
```

Open the list first and **comment out** (`#`) anything you actually use.

Try it without changing anything:

```sh
DRY_RUN=1 tools/debloat.sh models/egate-atom-3x/debloat.txt
```

## No list yet: build one

### 1. Scan

```sh
tools/scan.sh
```

This prints (and saves to `scans/`) the device properties, which app is Home, the screensaver, and
every package with its APK path. It changes nothing.

### 2. Sort the packages

Go through **"System packages, enabled"**. The APK path helps: `/system/app` and `/product/app` are
usually removable extras, while `/system/priv-app` and `/vendor` are often firmware plumbing.

| Usually safe to disable | Look for |
|---|---|
| Third-party app stores | `cm.aptoide.pt`, `com.htc.storeos`, vendor "app market" / "store" packages |
| Ad and content pushers | `*.ads*`, `*.push*`, `*.newroom`, `*.mbox*`, `*.recommend*`, `*.ad*` |
| Analytics and telemetry | `*.analytics*`, `*.statistics*`, `*.report*`, `*.bigdata*`, `*.tracker*` |
| Remote app installers | `*.apppush`, `*.silentinstall*`, `*.appmanager*` from the vendor |
| Forced updates | `*.ota`, `*.update*`, `*.upgrade*`. Your call: they also bring fixes |
| Ad screensavers | `*.screensaver` from the vendor (Clean Beam ships a clock screensaver) |
| Preloaded junk you don't use | Office suites, file managers, stock video/music players, browsers, games |
| Printing | `com.android.printspooler`, `com.android.bips`, `com.android.printservice.recommendation` |

| Never disable | Why |
|---|---|
| `android`, `com.android.systemui`, `com.android.shell`, `com.android.settings`, `com.android.tv.settings` | Android itself |
| Package installer, permission controller, `ext.services`, `ext.shared`, `modulemetadata`, `com.android.providers.*` | The projector may never boot again |
| Play Store, Play Services, `com.google.android.gsf` | Apps stop installing and updating |
| The keyboard (`*inputmethod*`, LeanKeyboard) | You can't type the Wi-Fi password again |
| **The projector's own controls**: focus, keystone, picture, input source, the vendor settings app | You lose focus and keystone |
| The **TV input / HDMI** layer (`*.tvinput`, `*.tv.settings`, `com.droidlogic.*`, `com.softwinner.*` input services) | HDMI shows "No signal" |
| Bluetooth and remote-control services | The IR / Bluetooth remote stops working |
| Screen mirroring (`eshare`, `miracast`, `cast`) | *Keep it if you cast from your phone* |
| Whatever is **Home** right now | Black screen. Install Clean Beam's launcher first |

`tools/debloat.sh` enforces the first rows itself. It **refuses** a list that contains core Android,
Play or keyboard packages, and it **skips** the current Home app.

When a name tells you nothing, ask the projector:

```sh
adb shell dumpsys package com.vendor.something | grep -E "Activity|Service|Receiver|permission" | head -40
```

A package that asks for `TV_INPUT_HARDWARE`, `WRITE_SECURE_SETTINGS` or `INJECT_EVENTS` is part of the
firmware's plumbing. Leave it unless you're sure.

### 3. Write the list

Create `models/<brand>-<model>/debloat.txt`, using lowercase with dashes, like `wanbo-x5-pro`. One package
per line, with comments saying why:

```
# Wanbo X5 Pro, Android 9, Allwinner H713. Network ADB on 5555.
# Kept on purpose: com.softwinner.tvd (HDMI input), com.wanbo.keystone (keystone/focus).

# App store nobody asked for
com.vendor.appstore

# Telemetry
com.vendor.analytics
```

### 4. Apply it, a few at a time

```sh
tools/debloat.sh models/wanbo-x5-pro/debloat.txt
```

Start with the obvious ones. Reboot (`adb reboot`) and check that **HDMI, focus, keystone, Wi-Fi,
Bluetooth, the remote and sound** still work. Then add more. If something broke:

```sh
tools/restore.sh          # undoes the most recent run
```

### 5. Share it

Add `device.json` and a short `README.md` next to the list (copy
[`models/egate-atom-3x/`](../../models/egate-atom-3x/)) and open a pull request. See
[CONTRIBUTING.md](../../CONTRIBUTING.md).

## What the script does

1. Checks every line is a bare package name, and refuses core packages.
2. Saves a **backup** to `backups/<device>-<date>/`: all packages, what was already disabled, Home,
   the screensaver, `getprop`.
3. For each package: skips it if it isn't installed, is already disabled or is the Home app, and
   otherwise runs `pm disable-user --user 0` and records it in `disabled-by-us.txt`.

`restore.sh` re-enables exactly what's in `disabled-by-us.txt`, so packages that were off before you
started stay off.

## Doing it by hand

No bash? Every step is one `adb` command:

```sh
adb shell pm list packages -s -e                 # enabled system packages
adb shell pm disable-user --user 0 com.vendor.analytics
adb shell pm enable com.vendor.analytics         # undo
adb shell pm list packages -d                    # what is disabled
```

Run them one at a time. On some firmware, commands chained with `;` silently stop after the first
one.

Next: [4. Launcher](04-launcher.md)
