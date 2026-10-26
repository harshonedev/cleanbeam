# 6. Restore

Everything Clean Beam does can be undone. Nothing was uninstalled from the firmware.

## Undo everything

```sh
tools/restore.sh
```

It reads the newest backup under `backups/` (written by `tools/debloat.sh`) and:

1. re-enables every package that run disabled (packages that were off before you started stay off),
2. puts the old Home screen back,
3. puts the old screensaver back.

To pick an older backup, run `tools/restore.sh backups/<device>-<date>`.

**Lost the backup folder?** (A different computer, or you deleted it.) Re-enable everything in a list
instead:

```sh
tools/restore.sh --list models/<your-model>/debloat.txt
```

Then remove Clean Beam's apps if you want them gone too:

```sh
adb uninstall com.cleanbeam.launcher
adb uninstall com.cleanbeam.remote
```

## Undo one piece

| To undo | Run |
|---|---|
| One disabled package | `adb shell pm enable com.vendor.something` |
| See what is disabled | `adb shell pm list packages -d` |
| The launcher as Home | `adb shell cmd package set-home-activity <stock.launcher/.Activity>`. `tools/scan.sh` lists the apps that can be Home |
| The screensaver | `adb shell settings put secure screensaver_components <old value from backups/*/screensaver-before.txt>` |
| The phone remote | `adb uninstall com.cleanbeam.remote` |

Some firmware needs a vendor-specific step to give Home back (EGate:
`adb shell setprop persist.jx.launcher.highpriority com.jixin.launcher5`). Check your model's
`README.md`.

## Last resort: factory reset

Settings → Device Preferences → **Reset** (or the projector's own "Restore factory settings"). This
re-enables every package and removes every app you installed, Clean Beam included. Your
Wi-Fi and accounts are wiped too.

## When you're done

If you don't use the phone remote, switch network ADB off: Developer options → **USB debugging**
and **Network debugging** off, then reboot. See [Security](02-connect-your-projector.md#security).
