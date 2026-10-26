# Troubleshooting

## Connecting

**`adb connect` says `Connection refused`.** Network ADB isn't on that port. Try `:3333`, then
`:5555`, then turn on Network / Wireless debugging in Developer options
([guide](02-connect-your-projector.md#neither-port-works)).

**`No route to host` or it times out.**
- macOS: give your terminal **Local Network** permission (System Settings → Privacy & Security →
  Local Network), then `adb kill-server`.
- The projector moved IP after a reboot. Run `adb mdns services` or check your router.
- Your router isolates Wi-Fi clients (common on guest and mesh networks). The projector answers ARP
  but nothing else. Use the main network, or connect the computer with a cable.
- Some projectors take a few minutes after boot before network ADB answers. Wait and retry.

**`unauthorized`.** Accept "Allow USB debugging?" on the projector. If the prompt never appears,
run `adb kill-server`, connect again, and watch the projector screen.

**`adb` crashes with `protocol fault`.** Run `adb kill-server; adb start-server`, then connect once.
Don't run two `adb connect` at the same time.

**Several devices and the scripts pick the wrong one.** `export ANDROID_SERIAL=<ip>:<port>`.

## Debloat

**`refusing ... core Android packages`.** That's the safety check. Remove those lines: disabling
them can stop the projector from booting.

**`WARN ... SecurityException`.** That firmware won't let `pm disable-user` touch that package. Leave
it.

**HDMI shows "No signal" after debloating.** You disabled part of the TV input layer. Run
`tools/restore.sh`, reboot (a full power cycle, not just standby), then re-apply the list without the
`*tvinput*`, `*.tv.settings`, `droidlogic` or `softwinner` input packages.

**The IR remote stopped working.** A remote or Bluetooth service was disabled. Use the phone remote
or `adb shell input keyevent`, then `tools/restore.sh`.

## Launcher

### Black screen after a reboot

Home was disabled, or Home points at an app that's gone. From the computer:

```sh
adb shell cmd package set-home-activity com.cleanbeam.launcher/.LauncherActivity
adb shell input keyevent 3
```

If the Clean Beam launcher isn't installed, re-enable the stock one instead
(`adb shell pm enable <stock launcher>`).

### Home snaps back to the stock launcher

- After `adb install -r` of the launcher, Android may reset the default Home. Set it again.
- Some firmware decides Home its own way and the stock launcher keeps rewriting it. Look for a
  property:
  ```sh
  adb shell getprop | grep -i launcher
  ```
  On EGate/Jixin boards it's `persist.jx.launcher.highpriority`, and setting it to
  `com.cleanbeam.launcher` fixes it for good. Found one on your projector? Add it to your model's
  README and open a PR.

**"My wallpaper" shows a flat colour.** The folder isn't readable by the launcher, usually because
plain `adb push` created it. Use `tools/push-wallpapers.sh`, or run
`adb shell chmod 2775 /sdcard/Android/data/com.cleanbeam.launcher/files/wallpapers`.

**The Home tab has no posters (Full).** No TMDB token, a wrong token (use the long *Read Access
Token*, not the short API key), or no internet. Settings → Launcher → TMDB access token, then
**Refresh Home**.

**My vendor screensaver keeps coming back.** Disable the vendor screensaver package and grant the
launcher `WRITE_SECURE_SETTINGS` ([guide](04-launcher.md#screensaver)).

**Focus or Keystone tiles are missing.** The launcher shows them only when it finds the projector's
own screen for them. Run `adb shell dumpsys package | grep -iE "keystone|focus"` and open an issue
with the output.

## Phone remote

**The app can't find the projector with the code.** Phone and projector must be on the same Wi-Fi,
with no client isolation. Use the QR code instead, which works over Bluetooth too.

**Paired, but stuck on "Connecting".** The receiver isn't running. Open Settings → Phone remote in
the launcher (that starts it), or run `adb shell am start -n com.cleanbeam.remote/.PairingActivity`.

**Keys do nothing.** Check `adb shell getprop ro.adb.secure` is `0` or empty. Then check the helper:
`adb shell cat /data/local/tmp/projector-injector.log`. Receiver logs:
`adb logcat -s CleanBeamRemote`.

**It disconnects when the projector goes to sleep.** Expected: sleep turns Wi-Fi and Bluetooth off
on most boards. Keep "Keep Wi-Fi awake in standby" on in the launcher, and wake with the IR remote.

## Still stuck

Open an issue with your projector model, the output of `tools/scan.sh`, and what you ran. Using an
AI assistant? The "Troubleshoot" prompt in [PROMPTS.md](../../PROMPTS.md) gives it the right context.
