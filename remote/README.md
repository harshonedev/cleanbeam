# EGate Remote

Phone remote control for the EGate Atom 3X projector. Two apps, one protocol (`PROTOCOL.md`):

| Part | Where | What |
|------|-------|------|
| Receiver | `receiver/` (Kotlin, `io.egate.remote`) | Runs on the projector. Listens over Wi-Fi (WebSocket) and Bluetooth LE, injects key/pointer/text input. |
| Phone app | `phone/` (Flutter, `io.egate.remote_app`) | Android now, iOS later from the same code. D-pad, apps grid, touchpad, keyboard. |

## Receiver: build, install, pair

```bash
export ANDROID_SERIAL=192.168.10.52:3333     # projector's root adbd
tools/install-remote.sh --build              # builds, installs, grants, starts service, opens pairing screen
```

The pairing screen shows a QR code and a 6-digit PIN. The PIN is single-use and only valid while that screen is open; after five wrong attempts it is cancelled (reopen the screen for a new one). "Re-pair (new token)" invalidates every phone that was paired before.

How it works on the projector:
- `RemoteService` (foreground) owns everything and restarts on boot.
- It speaks the ADB protocol to the projector's own adbd on `127.0.0.1:3333` (root, no auth on this userdebug build) to launch `app_process … io.egate.remote.injector.Main --uid <app uid>`. That helper injects input through `InputManager.injectInputEvent` and only accepts connections from the receiver app's uid. If the helper dies, the next command respawns it.
- Wi-Fi: `ws://<ip>:7272/`, mDNS `_egate-remote._tcp` as "EGate Atom3X", app icons at `http://<ip>:7273/icon/<pkg>`.
- BLE: advertises as "EGate Remote" with service `b6517522-6d7b-49d2-8488-664819daea01`.

Smoke test from the Mac (token is in `/data/data/io.egate.remote/shared_prefs/pairing.xml`):

```bash
node tools/wsctl.mjs 192.168.10.52 <token> '{"t":"key","code":20,"action":"tap"}' '{"t":"apps"}' '{"t":"status"}'
node tools/wsctl.mjs 192.168.10.52 --pair <pin> '{"t":"ping"}'     # manual pairing path
```

Logs: `adb logcat -s EGateRemote`.

## Phone app

```bash
cd phone
flutter test && flutter analyze
(cd android && ./gradlew assembleDebug)       # reliable build; `flutter build apk` may try to download Gradle
adb -s <phone> install -r build/app/outputs/flutter-apk/app-debug.apk
```

First launch: scan the projector's QR, or tap "Enter the 6-digit code instead" (phone and projector must share a Wi-Fi for code pairing). Afterwards the app connects on its own: it tries Wi-Fi (mDNS, then the saved IP) and BLE at the same time and prefers Wi-Fi when both answer within 3 s. The badge in the top right shows which link is in use. With Wi-Fi off the app falls back to BLE and the apps grid shows letter tiles instead of icons.

Screens: **Remote** (D-pad with press-and-hold, Back/Home/Menu, volume, media, "Type" keyboard sheet, "Power menu"), **Apps** (tap to launch, long-press to pin as favourite, pull down to refresh), **Touchpad** (drag to move, tap to click, two fingers to scroll).

## What the projector cannot do

- **Wake from the phone.** Sleep (`KEYCODE_SLEEP` or the power menu's "Screen Off") is a full suspend: Wi-Fi and Bluetooth go off and Wake-on-LAN is ignored. Use the IR remote or the device button to wake. The box also suspends after 5 idle minutes.
- **Focus / keystone.** Not Android keys on this firmware. The IR power button is keycode 135 and opens the projector's power menu; that is the only projector-specific key exposed ("Power menu").

## Rollback

```bash
adb uninstall io.egate.remote      # helper dies at next reboot, or: adb shell pkill -f injector.Main
```

Nothing else on the projector is changed.
