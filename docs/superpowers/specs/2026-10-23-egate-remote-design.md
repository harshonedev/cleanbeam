# EGate Remote — Phone Remote Control for EGate Atom 3X — Design

**Date:** 2026-10-23
**Device:** EGate Atom 3X projector (`t950s_be311`), Android 13 / SDK 33, userdebug, `ro.secure=0`, `ro.adb.secure=0`, adbd runs as root and listens on TCP 3333 (`persist.adb.tcp.port=3333`). Bluetooth Classic + BLE present, BLE advertising enabled, GATT enabled. Already running our custom launcher `io.egate.launcher` (see `2026-10-23-egate-launcher-design.md`).
**Dev machine:** macOS, Flutter at `~/development/flutter`, Xcode 27.1, Android SDK at `~/Library/Android/sdk`, Java via jenv. Existing Kotlin/Gradle launcher project at `launcher/`.

## Intent / why
The projector's IR remote is slow and intermittently fails. User wants "full control" of the projector from their phone. Phase 1 target: Android phone (user has a spare Android device). iOS later from the same codebase; the iOS build question (free vs paid Apple account) is deferred.

## Goals
1. Phone app that fully replaces the IR remote: navigation, media, volume, power, app launching, text entry, pointer.
2. Two transports, same protocol: **Wi-Fi** (fast, primary when reachable) and **BLE** (works when Wi-Fi client isolation blocks phone→projector unicast, or phone is on a different network).
3. One-time pairing by scanning a QR code shown on the projector screen. Afterwards the app connects automatically on open.
4. Projector-side receiver survives reboot with no user action, with no system partition changes (same constraint as the launcher project).
5. Latency: button press to on-screen reaction well under 100 ms on Wi-Fi; BLE noticeably better than IR.

## Non-goals
- iOS build/signing in this phase (code must stay cross-platform; no Android-only Flutter packages without an iOS path).
- Screen mirroring / casting / screenshots to phone.
- Voice input.
- Controlling multiple projectors (one paired device; design stores a list but UI shows one).
- Hardening against a hostile LAN. Pairing token stops casual neighbors, that's the bar (home entertainment box, user's earlier decision).

## Anti-goals (would be failure)
- Receiver breaks the launcher, boot, or ADB access.
- Receiver needs manual restart after reboot.
- App requires Wi-Fi to work at all (BLE must be a full fallback for every control, except where noted below).
- Noticeable lag or dropped key presses on repeated D-pad presses.

## Architecture

```
┌──────────────── Phone (Flutter) ────────────────┐      ┌────────────── Projector ───────────────────────┐
│ UI: Remote / Apps / Keyboard / Touchpad / Pair  │      │ io.egate.remote (Kotlin app, foreground svc)   │
│ Transport layer:                                │ WiFi │  ├ WebSocket server :7272 + mDNS _egate-remote │
│   WsTransport  ──────────────────────────────── ┼──────┼─▶├ BLE GATT server (advertises "EGate Remote")  │
│   BleTransport ──────────────────────────────── ┼──BLE─┼─▶├ CommandRouter (auth, dispatch)               │
│ Same JSON command protocol over both            │      │  └ InjectorClient ─LocalSocket─▶ Injector helper│
└─────────────────────────────────────────────────┘      │        (shell/root, started via localhost adbd) │
                                                         │        InputManager.injectInputEvent             │
                                                         └────────────────────────────────────────────────┘
```

### Projector receiver: `io.egate.remote` (Kotlin, Android app)
- minSdk 28, compileSdk/targetSdk 34, Kotlin, same Gradle setup as `launcher/`. Lives at `remote/receiver/`.
- **Foreground service** started on `BOOT_COMPLETED` and on app launch. Holds: BLE GATT server, WebSocket server, mDNS registration, Injector connection.
- **Privilege bootstrap (my call, verified feasible):** the app cannot inject key events into other apps (`INJECT_EVENTS` is signature-level). The projector's adbd is root and unauthenticated on `127.0.0.1:3333`. The service implements a minimal ADB client (CNXN → OPEN `shell:` → WRTE/OKAY/CLSE, no AUTH step because `ro.adb.secure=0`) and runs:
  `CLASSPATH=<our apk path> app_process /system/bin io.egate.remote.injector.Main`
  This is the scrcpy/Shizuku pattern. The helper runs as root (adbd's uid), creates a `LocalServerSocket("egate-injector")`, and accepts one connection from the app. The app sends it newline-delimited JSON commands. If the helper is already running (service restart), the app just reconnects. If the helper dies, the service restarts it.
  Verify at plan step 1 that `:3333` is listening right after a cold boot *without* `adb root` having been run. Root is not required: shell uid (2000) holds `INJECT_EVENTS`, which is all the helper needs (scrcpy works this way); only `reboot` needs root and degrades gracefully. The gate is "adbd on localhost without AUTH at boot". Reviewer and I both expect it to pass (`ro.secure=0`, `persist.adb.tcp.port=3333`). If it fails there is no acceptable fallback (Shizuku needs a wireless-debugging pairing that this device drops on reboot; apps cannot `setprop`); the build stops and the user is told.
- **Injector helper** (`io.egate.remote.injector.Main`, plain Java/Kotlin, runs under `app_process`): uses `InputManager.injectInputEvent` via reflection (same API scrcpy uses on Android 13) for key events and mouse motion; `KeyCharacterMap.VIRTUAL_KEYBOARD` to turn text into key events; falls back to `/system/bin/input text` for characters that don't map. Executes `reboot` and `svc power` style commands via `Runtime.exec` for power actions.
- **Things the app does itself (no helper needed):** list installed launchable apps (`queryIntentActivities`, LEANBACK_LAUNCHER + LAUNCHER, same code as the launcher), launch an app (`startActivity` with `FLAG_ACTIVITY_NEW_TASK`), report foreground app (`UsageStatsManager`, grant via `appops set io.egate.remote GET_USAGE_STATS allow` at install), report volume and screen state, show the pairing QR screen.
- **Pairing screen** (an Activity): shows a QR code encoding `{"v":1,"name":"EGate Atom3X","ip":"<wifi ip>","port":7272,"token":"<32 random bytes base64>"}` plus a **6-digit pairing PIN** for manual entry. The PIN is random, regenerated every time the pairing screen opens, and only honoured while the screen is visible: the phone sends `pair {code}` and receives `pair_ok {token,name}` (the full token), after which it authenticates normally. Token is generated once and stored in app prefs; "Re-pair" on the screen regenerates it. Opened from the launcher's app grid or via `am start`. (Apps cannot read the Bluetooth adapter MAC on Android 6+, and the advertiser uses a random address, so the phone identifies the projector over BLE by service UUID, not MAC.)
- **Wi-Fi transport:** WebSocket server on port 7272 (library: `org.java-websocket:Java-WebSocket`). mDNS service `_egate-remote._tcp` via `NsdManager`, TXT record carries `name` and `ble` MAC. Text frames, one JSON object per frame.
- **BLE transport:** GATT server with one service UUID (fixed, generated once and written in the shared protocol doc). Characteristics: `cmd` (write without response, phone→projector), `evt` (notify, projector→phone). Requests MTU 512. Messages are the same JSON as Wi-Fi; a message longer than MTU-3 is split into chunks prefixed with a 1-byte `[seq|last]` header (only app-list replies are expected to need this). Advertises local name "EGate Remote" with the service UUID; connectable always (receiver reboots into advertising state).
- **Auth:** first message on any connection must be `{"t":"auth","token":"..."}` or, while the pairing screen is open, `{"t":"pair","code":"123456"}`. Everything else before a valid auth is dropped and the connection closed after 3 bad attempts. Token compared with constant-time equality. (Wi-Fi is plaintext `ws://`. Token sniffable on-LAN; accepted per non-goals.)

### Protocol (JSON, identical over WS and BLE)
Phone → projector (`t` = type):
- `auth {token}`; `pair {code}` (only while pairing screen is showing) → `pair_ok {token,name}` / `pair_fail {}`
- `key {code:<Android KEYCODE int>, action:"tap"|"down"|"up"}` — D-pad, OK, Back, Home, Menu, volume, mute, media keys, power. `tap` = down+up in the helper (one round trip, keeps repeated presses fast). `down`/`up` used for press-and-hold (long-press OK, held volume).
- `text {s:"..."}` — typed string, injected as key events. `key` with `KEYCODE_DEL`/`ENTER` for editing.
- `mouse {dx, dy}` relative move; `click {button:"left"|"right"}`; `scroll {dy}`. Projector shows the system pointer; helper injects `SOURCE_MOUSE` motion events.
- `launch {pkg}`; `apps {}` → reply list.
- `power {action:"sleep"|"wake"|"reboot"|"toggle"}` — sleep/wake via `KEYCODE_SLEEP`/`KEYCODE_WAKEUP`; reboot via helper; `toggle` = `KEYCODE_POWER`. **Open hardware question (measured in plan Task 1):** if "sleep" on this Amlogic box is deep suspend, Wi-Fi and BLE stop and `wake` cannot reach the receiver; then the phone hides "wake" and the user wakes with the IR remote or the device button. Power-on from fully off is never possible from the phone.
- **Projector-specific keys** (focus, keystone, input source, projector settings): the IR remote's key layout under `/system/usr/keylayout/` is inspected in plan Task 1. Any such keys that map to Android keycodes get an "Extra" row on the phone's Remote screen; any that are handled outside Android input (MCU/serial) are out of scope for phase 1 and listed in the README.
- `status {}`.
- `ping {}`.

Projector → phone:
- `auth_ok {name, version}` / `auth_fail {}` / `pair_ok {token,name}` / `pair_fail {}`
- `apps {items:[{pkg,label}]}` (icons fetched over Wi-Fi only, via `GET http://<ip>:7273/icon/<pkg>` from the same server; over BLE the app shows label-only tiles, with icons cached from any earlier Wi-Fi session)
- `status {app:<pkg>, screenOn:bool, volume:int, maxVolume:int}` sent on request and pushed on change (foreground app poll every 2 s while a client is connected)
- `pong {}`; `err {msg}`.

### Phone app: `remote/phone/` (Flutter, package `io.egate.remote_app`)
- Flutter stable from `~/development/flutter`. Android `minSdk 26`. Packages (all with iOS support): `flutter_blue_plus` (BLE central), `web_socket_channel` (Wi-Fi), `nsd` or `multicast_dns` (discovery; builder picks whichever works on both platforms), `mobile_scanner` (QR), `shared_preferences` (pairing store), `wakelock_plus` not needed.
- **Transport manager:** on open, if a pairing exists: try mDNS (2 s) then the stored IP directly (1 s) over WebSocket; in parallel start a BLE scan filtered on the service UUID. First to authenticate wins; prefer Wi-Fi if both come up within 3 s. Shows a small transport badge ("Wi-Fi" / "BLE") and reconnects automatically with backoff on drop. Keepalive `ping` every 10 s on Wi-Fi.
- **Screens:**
  1. **Pair:** camera QR scan, or type the 6-digit code + pick from mDNS/BLE scan list. Stores the pairing.
  2. **Remote (home tab):** power, status line (foreground app name, transport badge), big D-pad with centre OK, Back / Home / Menu row, volume −/mute/+, play-pause/rew/fwd row. Haptic tick on press. Press-and-hold on OK and volume sends `down`…`up`.
  3. **Apps tab:** grid of installed apps from `apps`, tap to launch, favourites pinned to top (stored locally).
  4. **Touchpad tab:** full-screen pad, drag = move, tap = click, two-finger drag = scroll.
  5. **Keyboard:** a text field sheet reachable from the Remote tab. Sends characters as typed (per keystroke `text` of one char, backspace as `KEYCODE_DEL`) so projector search boxes update live; Enter sends `KEYCODE_ENTER`.
- **Look:** dark theme, large touch targets, D-pad-first. Exact visual details left to builder; must not look like a stock Material template (use `frontend-design` skill at build time).
- **Android specifics:** `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` + location permission flow for Android 12+ scanning, camera permission for QR.

## Repo layout
```
e-gate/
  launcher/            (existing)
  remote/
    receiver/          Kotlin Android app for the projector, incl. injector/ source set
    phone/             Flutter app
    PROTOCOL.md        the JSON protocol + UUIDs, single source of truth for both sides
  tools/install-remote.sh   adb install receiver, grant appops, start service, open pairing screen
```
`e-gate/` is not a git repository. The launcher project was built without one. Builder initialises git at `e-gate/` as the first step (my call; the user can veto) so the remote work has history.

## Install / run on projector
`tools/install-remote.sh`: `adb install -r`, `appops set io.egate.remote GET_USAGE_STATS allow`, `pm grant` for `POST_NOTIFICATIONS` (foreground service notification), `am start-foreground-service`, then `am start` the pairing activity. Receiver also appears as a tile in our launcher ("Remote pairing").

## Verification (done at the end of the plan, on real hardware)
- Cold reboot projector → within 60 s the receiver is advertising on BLE and listed on mDNS, with no ADB interaction. Helper process visible in `ps -A` as root.
- Phone app connects over Wi-Fi; D-pad moves focus in our launcher and in YouTube; typing in YouTube search works; launching apps works; volume and sleep/wake work.
- Disable Wi-Fi on the phone → app falls back to BLE within ~5 s; all controls still work (apps list shows labels without icons).
- Measured: 20 rapid D-pad presses over each transport, none dropped.

## Rollback
- `adb uninstall io.egate.remote` (helper dies with the next reboot; or `kill` it).
- Nothing else on the device is changed.

## Left to builder
- Exact UI layout, colours, icons, haptics of the phone app.
- Chunking details of BLE messages and MTU negotiation retries.
- Whether the icon HTTP endpoint shares the WebSocket port (library permitting) or uses 7273.
- Which mDNS Flutter package.
- Error copy, reconnection backoff values.
