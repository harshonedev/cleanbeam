# Clean Beam Remote

Phone remote control for Android projectors. Two apps, one protocol (`PROTOCOL.md`):

| Part | Where | What |
|------|-------|------|
| Receiver | `receiver/` (Kotlin, `com.cleanbeam.remote`) | Runs on the projector. Listens over Wi-Fi (WebSocket) and Bluetooth LE, injects key/pointer/text input. |
| Phone app | `phone/` (Flutter, `com.cleanbeam.phone`) | Android now, iOS later from the same code. D-pad, apps grid, touchpad, keyboard. |

## Receiver: build, install, pair

```bash
export ANDROID_SERIAL=<projector-ip>:5555   # or :3333, whichever adbd is listening on
tools/install-remote.sh --build              # builds, installs, grants, starts service, opens pairing screen
```

The pairing screen shows a QR code and a 6-digit PIN. The PIN is single-use and only valid while that screen is open; after five wrong attempts it is cancelled (reopen the screen for a new one). "Re-pair (new token)" invalidates every phone that was paired before.

How it works on the projector:
- `RemoteService` (foreground) owns everything and restarts on boot.
- It speaks the ADB protocol to the projector's own adbd on localhost. The port comes from `service.adb.tcp.port` / `persist.adb.tcp.port` when that property is set, otherwise it tries 5555 and then 3333. This only works when `ro.adb.secure=0` (no AUTH handshake), which is how most uncertified Android projectors ship; a certified device that requires wireless-debugging pairing cannot inject input. The client uses the `exec:` service to launch `nohup setsid … app_process … com.cleanbeam.remote.injector.Main --uid <app uid>` (the `shell:` service would kill the child when the stream closes). The helper injects keys and text through `InputManager.injectInputEvent`, types emoji and other unmapped text by setting the clipboard (as `com.android.shell`) and pressing Paste, drives the pointer by writing evdev events to a relative mouse node when the kernel has one (a `jixin_mouse` device is preferred, then any other mouse) and falls back to injected motion events, reports the focused app (`ActivityTaskManager`, then UsageStats), and starts apps and settings screens with `am start` because Android blocks activity starts from the service while the receiver has no visible window. It only accepts connections from the receiver app's uid. If the helper dies, the next command respawns it (first start of a new build takes ~5 s of dex verification); its stderr is in `/data/local/tmp/projector-injector.log`.
- Wi-Fi: `ws://<ip>:7272/`, mDNS `_cb-remote._tcp` under the projector's model name, app icons at `http://<ip>:7273/icon/<pkg>`.
- BLE: advertises service `b6517522-6d7b-49d2-8488-664819daea01`. The phone matches that UUID, not the adapter name.

Smoke test from the Mac (token is in `/data/data/com.cleanbeam.remote/shared_prefs/pairing.xml`):

```bash
node tools/wsctl.mjs <projector-ip> <token> '{"t":"key","code":20,"action":"tap"}' '{"t":"apps"}' '{"t":"status"}'
node tools/wsctl.mjs <projector-ip> --pair <pin> '{"t":"ping"}'     # manual pairing path
```

Logs: `adb logcat -s CleanBeamRemote`.

## Phone app

```bash
cd phone
flutter test && flutter analyze
(cd android && ./gradlew assembleDebug)       # reliable build; `flutter build apk` may try to download Gradle
adb -s <phone> install -r build/app/outputs/flutter-apk/app-debug.apk
```

First launch: scan the projector's QR, or tap "Enter the 6-digit code instead" (phone and projector must share a Wi-Fi for code pairing). Afterwards the app connects on its own: it tries Wi-Fi (mDNS, then the saved IP) and BLE at the same time and prefers Wi-Fi when both answer within 3 s. The badge in the top right shows which link is in use. With Wi-Fi off the app falls back to BLE and the apps grid shows letter tiles instead of icons.

Screens: **Remote** (D-pad with press-and-hold, Back/Home/Menu, volume, media, "Type" keyboard sheet, "Power menu", and a **Quick settings** row: Input source, Wi-Fi, Bluetooth, Focus, Keystone, Brightness, Projection, Projector settings, System settings; the row shows whatever the projector reports it can open), **Apps** (tap to launch, long-press to pin as favourite, pull down to refresh), **Touchpad** (drag to move, tap to click, two fingers to scroll). Focus, keystone, projection and the projector's own settings screen appear only when that box has an activity for them. Wi-Fi, Bluetooth, brightness and system settings are the Android screens when a vendor one is not installed.

The Type sheet sends every keystroke live (backspaces included, by grapheme so an emoji is one delete), has Backspace (hold to repeat), Clear (deletes what was typed from the sheet), Paste (phone clipboard, chunked) and Enter, and shows Live/Offline.

The projector's IP moves with DHCP. The receiver reports its current IP in `auth_ok`/`status`; the app saves it, and when it ends up on Bluetooth (stale IP, mDNS miss) it retries Wi-Fi with the reported address at once and then every 15 s, switching over silently.

### UI flows (Maestro)

```bash
maestro --device <phone serial> test maestro/remote_flow.yaml      # every control, status line, shortcuts, keyboard, apps, touchpad
adb -s <phone> shell svc wifi disable && maestro --device <phone> test maestro/ble_flow.yaml
adb -s <phone> shell svc wifi enable  && maestro --device <phone> test maestro/reconnect_flow.yaml
maestro --device <phone> test -e PIN=123456 -e IP=<projector-ip> maestro/pair_with_code.yaml   # app must be unpaired
```

Buttons carry accessibility ids equal to their widget keys (`dpad-ok`, `btn-home`, `shortcut-input`, `app-<pkg>`, `type-enter`, …).

## What the projector cannot do

- **Inject input without a local adbd that skips authentication.** The receiver cannot type or press keys on a locked-down Android TV that requires wireless-debugging pairing. Uncertified projector boards that leave adbd open on 5555 or 3333 work.
- **Wake from the phone.** Sleep (`KEYCODE_SLEEP`, or "Screen Off" in a vendor power menu) is a full suspend on the boards this was tried on: Wi-Fi and Bluetooth go off. Use the IR remote or the device button to wake.
- **Focus / keystone as keys.** Those are vendor screens, opened from Quick settings when the projector has them. **Power menu** sends the vendor IR power key on jixin boards and a long-press of the Android power key everywhere else.

## Rollback

```bash
adb uninstall com.cleanbeam.remote      # helper dies at next reboot, or: adb shell pkill -f injector.Main
```

Nothing else on the projector is changed.
