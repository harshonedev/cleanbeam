# EGate Remote Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Flutter phone app (Android first) that fully replaces the projector's IR remote over Wi-Fi or BLE, plus a Kotlin receiver app on the projector that injects the commands.

**Architecture:** Receiver app `io.egate.remote` runs a foreground service hosting a WebSocket server (+mDNS, +icon HTTP), a BLE GATT server, and a privilege bootstrap that uses the projector's unauthenticated root adbd on `127.0.0.1:3333` to spawn an `app_process` helper which injects input via `InputManager.injectInputEvent`. The phone app speaks one JSON protocol over either transport, racing both and preferring Wi-Fi.

**Tech Stack:** Receiver: Kotlin 1.9.24, AGP 8.5.2, Gradle 8.9, minSdk 28, compileSdk 34, Java-WebSocket 1.5.7, NanoHTTPD 2.3.1, ZXing core 3.5.3, org.json, JUnit4. Phone: Flutter 3.41.9 / Dart 3.11, packages `flutter_blue_plus`, `web_socket_channel`, `nsd`, `mobile_scanner`, `shared_preferences`, `cached_network_image`. Test tooling on Mac: Node 26 (built-in `WebSocket`) for a WS smoke client.

**Spec:** `docs/superpowers/specs/2026-10-23-egate-remote-design.md`

## Global Constraints

- Receiver: `applicationId io.egate.remote`, minSdk 28, compileSdk/targetSdk 34, Kotlin, same Gradle/AGP/wrapper versions as `launcher/`, signed with debug keystore. No system partition changes.
- Phone: Flutter package `io.egate.remote_app`, Android `minSdk 26`; every Flutter package must support iOS too.
- Protocol: JSON, one object per frame, field `t` = type; identical over WebSocket text frames and BLE. Ports: WebSocket 7272, icon HTTP 7273. mDNS type `_egate-remote._tcp`. BLE advertised name `EGate Remote`.
- Auth: first message `{"t":"auth","token":...}`; anything else before auth dropped; close after 3 bad attempts; constant-time compare.
- Device ADB target for all hardware steps: `export ANDROID_SERIAL=192.168.10.52:3333` (plain TCP root adb). If it is gone after a reboot, reconnect via the TLS pairing the launcher session used (`adb connect 192.168.10.52:<tls port>` then `adb root`, then `adb connect 192.168.10.52:3333`).
- Commits: no `Co-Authored-By` trailer, no "Generated with Claude Code" line.

## Review Focus

1. Rapid repeated D-pad presses (20 in 2 s) over BLE must not be dropped or reordered — Task 8 (chunker/ordering test) and Task 18 (hardware count).
2. Helper process dies (killed, or adbd restarted by `adb root`) while a phone is connected — the next command must respawn the helper, not fail silently — Task 6 test `respawns helper when socket closed`.
3. Phone opens with Wi-Fi off and Bluetooth on: must connect over BLE within ~5 s with no user action — Task 13 test `falls back to BLE when ws fails`.
4. Text containing characters with no keycode (emoji, non-ASCII) must not crash the helper; falls back to `input text` — Task 6 test `text with unmappable chars uses input fallback`.
5. Receiver reboot: BLE advertising restarts and the stale token is unchanged, so the phone reconnects without re-pairing — Task 10 hardware step.

---

## File Structure

```
e-gate/                                   (git init here, Task 1)
  remote/PROTOCOL.md                      message catalogue + UUIDs (Task 1)
  remote/receiver/                        Gradle project, module :app
    app/src/main/AndroidManifest.xml
    app/src/main/java/io/egate/remote/
      RemoteService.kt                    foreground service; owns everything below
      BootReceiver.kt                     BOOT_COMPLETED → start RemoteService
      PairingActivity.kt                  QR + 6-digit code screen
      PairingStore.kt                     token generate/load/regenerate (SharedPreferences)
      protocol/Message.kt                 sealed Command/Event types + JSON codec
      protocol/Chunker.kt                 BLE split/reassemble
      adb/AdbClient.kt                    minimal ADB client over localhost
      adb/AdbPacket.kt                    packet encode/decode
      injector/Main.kt                    app_process entry (runs as root)
      injector/InputInjector.kt           injectInputEvent via reflection
      injector/InjectorClient.kt          app side: spawn via AdbClient + LocalSocket
      router/CommandRouter.kt             auth + dispatch per connection
      router/AppsHandler.kt               list/launch/icons/foreground app
      transport/WsServer.kt               Java-WebSocket + NanoHTTPD icons + NsdManager
      transport/BleServer.kt              GATT server + advertiser
    app/src/test/java/io/egate/remote/    JUnit tests mirror the packages above
  remote/phone/                           Flutter app
    lib/protocol/message.dart             codec (mirrors Message.kt)
    lib/protocol/chunker.dart
    lib/transport/transport.dart          abstract Transport
    lib/transport/ws_transport.dart
    lib/transport/ble_transport.dart
    lib/transport/transport_manager.dart  race / prefer Wi-Fi / reconnect
    lib/pairing/pairing.dart              Pairing model + store
    lib/pairing/pair_screen.dart
    lib/ui/remote_screen.dart, apps_screen.dart, touchpad_screen.dart, keyboard_sheet.dart, app_shell.dart
    test/                                 mirrors lib/
  tools/install-remote.sh
  tools/wsctl.mjs                         Node WS smoke client
```

---

### Task 1: Repo init, protocol doc, cold-boot ADB verification

**Files:**
- Create: `.gitignore`, `remote/PROTOCOL.md`
- Modify: `docs/superpowers/specs/2026-10-23-egate-remote-design.md` (record the boot verification result)

**Interfaces:**
- Produces: the UUIDs and message catalogue every later task copies from `remote/PROTOCOL.md`:
  - Service UUID `6e400001-e9a7-4a3e-8f3b-7c1d2e5a0001`, `cmd` char `6e400002-…0001` (write no-response), `evt` char `6e400003-…0001` (notify). (Builder: generate the base once with `uuidgen`, write it here, use the same value everywhere.)
  - Message catalogue exactly as the spec's Protocol section, with the JSON examples written out.

- [ ] **Step 1: `git init` at `e-gate/`**, `.gitignore` covering `*/build/`, `.gradle/`, `local.properties`, `.dart_tool/`, `.flutter-plugins*`, `*.iml`, `.idea/`, `remote/phone/build/`. Commit the existing `launcher/`, `tools/`, `docs/` as `chore: import existing launcher project`.
- [ ] **Step 2: Write `remote/PROTOCOL.md`** (catalogue + UUIDs + chunk header format: byte0 = `seq & 0x7F | (last ? 0x80 : 0)`, payload follows; a message that fits in one write has `seq=0,last=1`).
- [ ] **Step 3: Verify cold-boot adbd state.** Run `adb reboot`, wait, reconnect, then:
  Run: `adb shell "ps -o user,etime,name -A | grep adbd; cat /proc/uptime; netstat -lnt | grep 3333"`
  Expected: `root` adbd with etime ≈ uptime (not restarted) and `:3333 LISTEN`. Record the result in the spec under a new "Verified" line. If adbd is not root or 3333 is absent, STOP and pick the spec's fallback (Shizuku) before Task 5.
- [ ] **Step 4: Measure sleep/wake and projector keys.** Run `adb shell "ls /system/usr/keylayout /vendor/usr/keylayout; cat /system/usr/keylayout/*remote* /vendor/usr/keylayout/*remote* /vendor/usr/keylayout/*ir* 2>/dev/null; getevent -pl | grep -E 'add device|name:'"` — list every keycode the IR remote emits that is not in the standard set (expect names like `SETTINGS`, `TV_INPUT`, `FOCUS`, `ZOOM_IN`, or vendor numbers ≥ 300). Then `adb shell input keyevent 223; sleep 5; adb shell dumpsys power | grep -E 'mWakefulness|mInteractive'` and check from the Mac whether `ping 192.168.10.52` still answers and the BLE name still shows in a scanner: record "sleep keeps radios on: yes/no". Wake with `adb shell input keyevent 224` (if ADB is gone, press the IR power button). Record both results in the spec under "Verified" and list the extra keycodes in `PROTOCOL.md` as the `extra` row for Task 15.
- [ ] **Step 5: Commit** `docs: remote protocol + boot verification`.

---

### Task 2: Receiver project scaffold

**Files:**
- Create: `remote/receiver/{settings.gradle.kts,build.gradle.kts,gradle.properties,gradlew,gradlew.bat,gradle/wrapper/*,local.properties}` (copy from `launcher/`, rootProject name `EGateRemote`), `remote/receiver/app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `RemoteService.kt`, `BootReceiver.kt`, `app/src/main/res/values/strings.xml`, launcher icon (reuse launcher's generator approach).

**Interfaces:**
- Produces: `class RemoteService : Service()` with `companion fun start(ctx: Context)`; notification channel `egate_remote`; `BootReceiver` registered for `BOOT_COMPLETED`.

- [ ] **Step 1: Write `app/build.gradle.kts`** with dependencies: `org.java-websocket:Java-WebSocket:1.5.7`, `org.nanohttpd:nanohttpd:2.3.1`, `com.google.zxing:core:3.5.3`, `androidx.core:core-ktx:1.13.1`, `androidx.appcompat:appcompat:1.7.0`, test `junit:junit:4.13.2`, `org.json:json:20240303` (testImplementation, so JSON works in JVM unit tests). `buildTypes.release.signingConfig = debug`.
- [ ] **Step 2: Write the manifest**: permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `ACCESS_WIFI_STATE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS` (tools:ignore ProtectedPermissions), `QUERY_ALL_PACKAGES`. Service `foregroundServiceType="connectedDevice"`. `BootReceiver` with `BOOT_COMPLETED` filter. `PairingActivity` exported with `LEANBACK_LAUNCHER` + `LAUNCHER` categories, label "Remote pairing".
- [ ] **Step 3: Implement `RemoteService`** that only posts the foreground notification ("EGate Remote running") and logs; `BootReceiver.onReceive` calls `RemoteService.start(ctx)` via `ContextCompat.startForegroundService`.
- [ ] **Step 4: Build and install.** Run: `cd remote/receiver && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start-foreground-service io.egate.remote/.RemoteService && adb shell dumpsys activity services io.egate.remote | grep -c RemoteService`
  Expected: build `BUILD SUCCESSFUL`, count ≥ 1.
- [ ] **Step 5: Commit** `feat(receiver): project scaffold with foreground service`.

---

### Task 3: Protocol codec (Kotlin)

**Files:**
- Create: `app/src/main/java/io/egate/remote/protocol/Message.kt`, `app/src/test/java/io/egate/remote/protocol/MessageTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed class Command { // phone → projector
    data class Auth(val token: String); data class Key(val code: Int, val action: KeyAction)
    data class Text(val s: String); data class Mouse(val dx: Int, val dy: Int)
    data class Click(val button: String); data class Scroll(val dy: Int)
    data class Launch(val pkg: String); object Apps; data class Power(val action: String)
    object Status; object Ping; data class Unknown(val t: String) }
  enum class KeyAction { TAP, DOWN, UP }
  sealed class Event { // projector → phone
    data class AuthOk(val name: String, val version: String); object AuthFail
    data class Apps(val items: List<AppItem>); data class Status(val app: String?, val screenOn: Boolean, val volume: Int, val maxVolume: Int)
    object Pong; data class Err(val msg: String) }
  data class AppItem(val pkg: String, val label: String)
  object Codec { fun decodeCommand(json: String): Command; fun encodeEvent(e: Event): String }
  ```

- [ ] **Step 1: Write failing tests** `decodes key tap`, `decodes auth`, `unknown type becomes Unknown`, `malformed json becomes Unknown("")`, `encodes status`, `encodes apps list` with exact JSON strings from `PROTOCOL.md`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*MessageTest*'` → FAIL (unresolved reference).
- [ ] **Step 3: Implement `Codec`** with `org.json`.
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Commit** `feat(receiver): protocol codec`.

---

### Task 4: ADB local client

**Files:**
- Create: `adb/AdbPacket.kt`, `adb/AdbClient.kt`, test `adb/AdbPacketTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class AdbPacket(val cmd: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {
    fun encode(): ByteArray; companion { fun decode(input: InputStream): AdbPacket; const val A_CNXN=0x4e584e43; A_OPEN=0x4e45504f; A_OKAY=0x59414b4f; A_CLSE=0x45534c43; A_WRTE=0x45545257 } }
  class AdbClient(host: String = "127.0.0.1", port: Int = 3333) : Closeable {
    fun connect()                                   // CNXN(0x01000001, 256*1024, "host::\u0000"), expect CNXN back
    fun shell(cmd: String, timeoutMs: Long = 10_000): String   // OPEN "shell:$cmd\u0000", collect WRTE payloads until CLSE
    fun shellDetached(cmd: String)                  // OPEN, send OKAY, return without waiting (for app_process)
  }
  ```
  Header layout: 6 little-endian u32: cmd, arg0, arg1, len, checksum (sum of payload bytes), magic (`cmd xor 0xffffffff`). Checksum may be 0 when the remote's version ≥ 0x01000001; compute it anyway.

- [ ] **Step 1: Write failing tests** `encodes CNXN header fields in little endian`, `magic is complement of cmd`, `decode round-trips encode`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement `AdbPacket` and `AdbClient`.** `AdbClient` uses a plain `java.net.Socket`; must answer each incoming `WRTE` with `OKAY(localId, remoteId)`.
- [ ] **Step 4: Run unit tests** → PASS.
- [ ] **Step 5: Hardware check.** Add a temporary debug action: `adb shell am start-foreground-service -a io.egate.remote.DEBUG_SHELL --es cmd id io.egate.remote/.RemoteService`; the service runs `AdbClient().apply{connect()}.shell(cmd)` and logs the result.
  Run: `adb logcat -d -s EGateRemote | tail -3`
  Expected: `uid=0(root)`.
- [ ] **Step 6: Commit** `feat(receiver): minimal adb client to local root adbd`.

---

### Task 5: Injector helper (runs under app_process as root)

**Files:**
- Create: `injector/Main.kt`, `injector/InputInjector.kt`, test `injector/InjectorCommandTest.kt` (parsing only; injection needs hardware)

**Interfaces:**
- Produces: helper reads newline-delimited JSON lines on `LocalServerSocket("egate-injector")` (abstract namespace). Lines are exactly the `Command` JSON from Task 3 for types `key`, `text`, `mouse`, `click`, `scroll`, `power`, plus `{"t":"hello"}` → replies `{"t":"hello_ok","pid":N}\n`. Every other line replies `{"t":"ok"}\n` or `{"t":"err","msg":...}\n`.
  ```kotlin
  object Main { @JvmStatic fun main(args: Array<String>) }       // Looper.prepare(); loop accept; one client at a time
  class InputInjector {
    fun key(code: Int, action: KeyAction): Boolean               // KeyEvent with SOURCE_KEYBOARD (SOURCE_DPAD for 19..23), deviceId -1, flags FLAG_FROM_SYSTEM
    fun text(s: String): Boolean                                 // KeyCharacterMap.load(VIRTUAL_KEYBOARD).getEvents(chars); unmappable → Runtime.exec("input text <shell-escaped>")
    fun mouseMove(dx: Int, dy: Int); fun click(button: String); fun scroll(dy: Int)   // SOURCE_MOUSE MotionEvents; keep absolute cursor x,y clamped to display size from DisplayManager/SurfaceControl
    fun power(action: String)                                    // sleep→key 223, wake→key 224, toggle→key 26, reboot→Runtime.exec("reboot")
  }
  ```
  Injection via reflection: `android.hardware.input.InputManager.getInstance()` → `injectInputEvent(InputEvent, int mode)` with mode 0 (`INJECT_INPUT_EVENT_MODE_ASYNC`). Tap = DOWN then UP with the same `downTime`.

- [ ] **Step 1: Write failing tests** for `InjectorCommandTest`: `tap key sends down then up` and `text with unmappable chars uses input fallback` — structure `InputInjector` so the raw `inject(InputEvent)` and `exec(String)` calls go through an injectable interface `Sink` that tests replace with a recorder.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Hardware check (manual spawn).** Run:
  `adb shell "CLASSPATH=\$(pm path io.egate.remote | cut -d: -f2) app_process /system/bin io.egate.remote.injector.Main &"` then `adb shell "ps -A | grep app_process"` and from a second shell `adb shell "echo '{\"t\":\"key\",\"code\":20,\"action\":\"tap\"}' | nc -U -N @egate-injector"` (if `nc` lacks abstract-socket support, add `{"t":"selftest"}` handling that injects DPAD_DOWN once at startup when `args[0]=="selftest"`).
  Expected: focus moves down in our launcher on screen (`adb shell dumpsys activity top | grep -m1 'mFocused'` changes, or screenshot).
- [ ] **Step 6: Commit** `feat(receiver): root input injector helper`.

---

### Task 6: InjectorClient (spawn + reconnect)

**Files:**
- Create: `injector/InjectorClient.kt`, test `injector/InjectorClientTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class InjectorClient(private val spawner: Spawner, private val connector: Connector) {
    interface Spawner { fun spawn() }                       // real: AdbClient.shellDetached("CLASSPATH=$apk app_process /system/bin io.egate.remote.injector.Main")
    interface Connector { fun connect(): LineChannel? }     // real: LocalSocket to "egate-injector"; null if refused
    interface LineChannel : Closeable { fun send(line: String): String }  // write line, read one reply line
    @Synchronized fun send(json: String): String            // ensure connected (connect → if null, spawn, retry every 200 ms up to 5 s); on IOException drop channel and retry once
  }
  ```

- [ ] **Step 1: Write failing tests** `spawns when connect refused then connects`, `respawns helper when socket closed`, `does not spawn when already connected`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Wire into `RemoteService`:** on create, build real Spawner/Connector (apk path from `applicationInfo.sourceDir`) and call `injector.send("{\"t\":\"hello\"}")` on a background thread; log reply. Replace Task 4's debug action with `io.egate.remote.DEBUG_INJECT --es json '...'` that forwards to `injector.send`.
  Run: `adb shell am start-foreground-service -a io.egate.remote.DEBUG_INJECT --es json '{"t":"key","code":20,"action":"tap"}' io.egate.remote/.RemoteService; adb shell ps -A | grep -c app_process`
  Expected: focus moves; one helper process; killing it (`adb shell pkill -f injector.Main`) and re-sending still moves focus.
- [ ] **Step 6: Commit** `feat(receiver): injector client with auto-spawn`.

---

### Task 7: PairingStore, CommandRouter, AppsHandler

**Files:**
- Create: `PairingStore.kt`, `router/CommandRouter.kt`, `router/AppsHandler.kt`, tests `router/CommandRouterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class PairingStore(ctx: Context) { val token: String /* base64 of 32 random bytes, created on first read */; fun regenerate(): String; fun matches(candidate: String): Boolean /* constant-time */
    @Volatile var pairingPin: String? /* 6 random digits; set by PairingActivity.onResume, cleared in onPause */ ; fun newPin(): String }
  interface Session { fun send(json: String); fun close() }
  class CommandRouter(private val store: PairingStore, private val injector: (String) -> String, private val apps: AppsHandler, private val deviceName: String, private val version: String) {
    inner class Connection(private val session: Session) { fun onMessage(json: String) }   // state: unauthenticated(badAttempts) → authenticated
    fun newConnection(s: Session): Connection }
  class AppsHandler(ctx: Context) { fun list(): List<AppItem>; fun launch(pkg: String): Boolean; fun foregroundApp(): String?; fun iconPng(pkg: String): ByteArray?; fun volume(): Pair<Int,Int>; fun screenOn(): Boolean }
  ```
  Routing: `pair {code}` before auth → if `store.pairingPin != null && code == pin` reply `pair_ok {token,name}` and mark authenticated, else `pair_fail` (counts as a bad attempt); `key/text/mouse/click/scroll/power` → `injector(json)` verbatim; `apps` → `Event.Apps`; `launch` → `AppsHandler.launch`; `status` → `Event.Status`; `ping` → `Pong`; `Unknown` → `Err`.

- [ ] **Step 1: Write failing tests**: `drops commands before auth`, `closes after 3 bad auth`, `auth_ok then forwards key json to injector`, `pair with current pin returns token`, `pair fails when no pin active`, `apps returns list event`, `ping returns pong`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.** `AppsHandler.list` reuses the launcher's `AppRepository` logic (copy, hide `io.egate.remote` and `com.jixin.launcher5`). `foregroundApp` via `UsageStatsManager.queryEvents` last `ACTIVITY_RESUMED`. Volume via `AudioManager.STREAM_MUSIC`. Screen via `PowerManager.isInteractive`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(receiver): command router, pairing store, apps handler`.

---

### Task 8: BLE chunker (Kotlin)

**Files:**
- Create: `protocol/Chunker.kt`, test `protocol/ChunkerTest.kt`

**Interfaces:**
- Produces: `object Chunker { fun split(msg: ByteArray, mtu: Int): List<ByteArray>; class Assembler { fun feed(chunk: ByteArray): ByteArray? /* complete message or null */ } }` with header per `PROTOCOL.md` (`seq` 0..127 wraps, `last` bit 0x80, payload size `mtu - 3 - 1`).

- [ ] **Step 1: Failing tests** `single chunk when fits`, `splits 1000 bytes at mtu 23 into 53 chunks`, `assembler rebuilds`, `assembler discards on seq gap and restarts`, `twenty back-to-back single messages stay ordered`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement. Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(receiver): ble chunker`.

---

### Task 9: WebSocket server + mDNS + icon HTTP

**Files:**
- Create: `transport/WsServer.kt`, `tools/wsctl.mjs`
- Modify: `RemoteService.kt`

**Interfaces:**
- Produces: `class WsServer(port: Int = 7272, iconPort: Int = 7273, router: CommandRouter, apps: AppsHandler, ctx: Context) { fun start(); fun stop() }` — each WebSocket connection becomes a `Session`; NanoHTTPD serves `GET /icon/<pkg>` as `image/png` (from `AppsHandler.iconPng`, 404 if null); `NsdManager.registerService` with name `EGate Atom3X`, type `_egate-remote._tcp`, port 7272, no TXT needed.
- `tools/wsctl.mjs <ip> <token> <json...>`: connects, sends auth, then each JSON argument, prints every reply for 2 s, exits.

- [ ] **Step 1: Implement `WsServer`**, start it from `RemoteService.onCreate`, stop in `onDestroy`.
- [ ] **Step 2: Write `tools/wsctl.mjs`.**
- [ ] **Step 3: Hardware check.** Token: `adb shell "cat /data/data/io.egate.remote/shared_prefs/pairing.xml"` (as root). Also test manual pairing: open PairingActivity, read the PIN from a screenshot, `node tools/wsctl.mjs 192.168.10.52 --pair <pin> '{"t":"ping"}'` → `pair_ok` with token, then `pong`. Run: `node tools/wsctl.mjs 192.168.10.52 <token> '{"t":"key","code":20,"action":"tap"}' '{"t":"apps"}' '{"t":"status"}'`
  Expected: `auth_ok`, focus moves, apps list with ≥ 10 items, status with `app` = `io.egate.launcher`. Also `curl -o /tmp/i.png http://192.168.10.52:7273/icon/com.google.android.youtube.tv && file /tmp/i.png` → PNG. `dns-sd -B _egate-remote._tcp` on the Mac lists `EGate Atom3X`.
- [ ] **Step 4: Commit** `feat(receiver): websocket server, mdns, icon http`.

---

### Task 10: BLE GATT server, PairingActivity, boot persistence, install script

**Files:**
- Create: `transport/BleServer.kt`, `PairingActivity.kt`, `res/layout/activity_pairing.xml`, `tools/install-remote.sh`
- Modify: `RemoteService.kt`

**Interfaces:**
- Produces: `class BleServer(ctx: Context, router: CommandRouter) { fun start(); fun stop() }` — `BluetoothGattServer` with the service/characteristics from `PROTOCOL.md`; `cmd` writes feed a per-device `Chunker.Assembler`; events are `Chunker.split(bytes, negotiatedMtu)` and sent as notifications sequentially (wait for `onNotificationSent` before the next). `BluetoothLeAdvertiser` advertises connectable, includes service UUID, local name `EGate Remote` (set adapter name if needed via `BluetoothAdapter.setName`). Restart advertising on client disconnect.
- `PairingActivity`: full-screen dark layout, ZXing-rendered QR (`BitMatrix` → `Bitmap`) of `{"v":1,"name":"EGate Atom3X","ip":"<wifi ip>","port":7272,"token":"<token>"}`, the pairing PIN in large text (`store.newPin()` in `onResume`, `store.pairingPin = null` in `onPause`), a "Re-pair (new token)" button that calls `PairingStore.regenerate()` and redraws.
- `tools/install-remote.sh`: `adb install -r`, `pm grant io.egate.remote android.permission.BLUETOOTH_CONNECT`, `… BLUETOOTH_ADVERTISE`, `… POST_NOTIFICATIONS`, `appops set io.egate.remote GET_USAGE_STATS allow`, `am start-foreground-service io.egate.remote/.RemoteService`, `am start -n io.egate.remote/.PairingActivity`.

- [ ] **Step 1: Implement `BleServer`, `PairingActivity`, script.** Start `BleServer` from `RemoteService`.
- [ ] **Step 2: Hardware check: pairing screen.** Run `tools/install-remote.sh`; take `adb shell screencap -p /sdcard/p.png && adb pull /sdcard/p.png` and view: QR + 6-digit code visible.
- [ ] **Step 3: Hardware check: advertising.** Run: `adb shell dumpsys bluetooth_manager | grep -A3 'mAdvertisingServiceUuids'` → contains the service UUID. On the Mac, `system_profiler SPBluetoothDataType` or a scanner app shows `EGate Remote`.
- [ ] **Step 4: Hardware check: reboot persistence.** `adb reboot`, wait 90 s, reconnect. Run: `adb shell "dumpsys activity services io.egate.remote | grep -c RemoteService; ps -A | grep -c injector.Main; dumpsys bluetooth_manager | grep -c 6e400001"` after sending one `DEBUG_INJECT` key. Expected: `1`, `1`, `≥1`, and the token file unchanged.
- [ ] **Step 5: Commit** `feat(receiver): ble gatt server, pairing screen, boot persistence, install script`.

---

### Task 11: Flutter scaffold + protocol codec + Transport interface

**Files:**
- Create: `remote/phone/` via `flutter create --org io.egate --project-name remote_app --platforms android,ios remote/phone`, then `lib/protocol/message.dart`, `lib/protocol/chunker.dart`, `lib/transport/transport.dart`, tests `test/protocol/message_test.dart`, `test/protocol/chunker_test.dart`.

**Interfaces:**
- Produces:
  ```dart
  // message.dart — plain maps, thin helpers; mirrors PROTOCOL.md
  Map<String,dynamic> auth(String token); Map pair(String code); Map key(int code, {String action='tap'}); Map text(String s); Map mouse(int dx,int dy); Map click(String button); Map scroll(int dy); Map launch(String pkg); Map apps(); Map power(String action); Map status(); Map ping();
  class AppItem { final String pkg, label; }   class StatusInfo { String? app; bool screenOn; int volume, maxVolume; }
  Event parseEvent(String json)  // sealed-ish: AuthOk, AuthFail, PairOk(token,name), PairFail, AppsEvent(List<AppItem>), StatusEvent(StatusInfo), Pong, ErrEvent(msg), UnknownEvent
  // chunker.dart
  List<Uint8List> splitChunks(Uint8List msg, int mtu); class ChunkAssembler { Uint8List? feed(Uint8List chunk); }
  // transport.dart
  enum TransportKind { wifi, ble }
  abstract class Transport { TransportKind get kind; Stream<Event> get events; Future<void> connect(); void send(Map<String,dynamic> msg); Future<void> close(); Stream<bool> get connected; }
  ```
  `pubspec.yaml` deps: `flutter_blue_plus: ^1.35.0`, `web_socket_channel: ^3.0.0`, `nsd: ^2.3.0`, `mobile_scanner: ^7.0.0`, `shared_preferences: ^2.3.0`, `cached_network_image: ^3.4.0`. Android `minSdk 26` in `android/app/build.gradle.kts`.

- [ ] **Step 1: `flutter create`**, set minSdk, add deps, `flutter pub get`.
- [ ] **Step 2: Failing tests** mirroring Task 3 and Task 8 cases (same JSON strings, same chunk counts). Run `flutter test` → FAIL.
- [ ] **Step 3: Implement. Step 4: `flutter test`** → PASS.
- [ ] **Step 5: Commit** `feat(phone): flutter scaffold, protocol codec, chunker`.

---

### Task 12: WsTransport + BleTransport

**Files:**
- Create: `lib/transport/ws_transport.dart`, `lib/transport/ble_transport.dart`, tests `test/transport/ws_transport_test.dart` (uses a local `dart:io` `HttpServer` WebSocket echo/auth stub), `test/transport/ble_transport_test.dart` (chunk reassembly through a fake characteristic stream).

**Interfaces:**
- Produces: `class WsTransport implements Transport { WsTransport(String host, int port, {String? token, String? pairCode}); String? get pairedToken; }` — connects, sends `auth` first (or `pair` when `pairCode` is given; `pair_ok` sets `pairedToken`), resolves `connect()` on `auth_ok`, throws `AuthFailed` on `auth_fail`, throws `TimeoutException` after 3 s; pings every 10 s; `connected` emits false on socket close.
  `class BleTransport implements Transport { BleTransport(String deviceId /* flutter_blue_plus remoteId */, String token, {BleGatt gatt}); }` — `BleGatt` is a small abstraction over `flutter_blue_plus` (connect, discover, requestMtu(512), write cmd, subscribe evt) so tests inject a fake. Outgoing messages go through `splitChunks(bytes, mtu)`, written with `withoutResponse: true`; incoming notifications through `ChunkAssembler`.

- [ ] **Step 1: Failing tests** `ws connect resolves on auth_ok`, `ws connect throws on auth_fail`, `ble reassembles chunked apps event`, `ble sends 20 keys in order`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement. Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(phone): websocket and ble transports`.

---

### Task 13: Pairing model + TransportManager

**Files:**
- Create: `lib/pairing/pairing.dart`, `lib/transport/transport_manager.dart`, tests `test/transport/transport_manager_test.dart`

**Interfaces:**
- Produces:
  ```dart
  class Pairing { String name, ip, token; int port; /* fromQrJson, toJson */ }
  class PairingStore { Future<Pairing?> load(); Future<void> save(Pairing p); Future<void> clear(); }   // shared_preferences key "pairing"
  class TransportManager extends ChangeNotifier {
    TransportManager({required Pairing pairing, required Future<String?> Function(String name) resolveMdns, required Transport Function(String host) makeWs, required Future<String?> Function() findBle, required Transport Function(String id) makeBle});
    Transport? get active; TransportKind? get kind; bool get connecting; String? get lastError;
    Future<void> start();   // race: mDNS(2 s)→ws, stored ip→ws(1 s), BLE scan→ble; first auth_ok wins; if ws arrives within 3 s of a BLE win, switch to ws
    void send(Map msg);     // no-op + lastError if not connected
    Stream<Event> get events;
    Future<void> stop();    // reconnect with backoff 1,2,4,8 s (cap) on drop while started
  }
  ```

- [ ] **Step 1: Failing tests** `prefers wifi when both succeed`, `falls back to BLE when ws fails`, `switches to wifi if it arrives within 3s`, `reconnects with backoff after drop` (use `fake_async`).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement. Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(phone): pairing store and transport manager`.

---

### Task 14: Pair screen + app shell

**Files:**
- Create: `lib/pairing/pair_screen.dart`, `lib/ui/app_shell.dart`, modify `lib/main.dart`
- Modify: `android/app/src/main/AndroidManifest.xml` (CAMERA, BLUETOOTH_SCAN `neverForLocation`, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION, INTERNET), `ios/Runner/Info.plist` (NSCameraUsageDescription, NSBluetoothAlwaysUsageDescription, NSLocalNetworkUsageDescription, NSBonjourServices `_egate-remote._tcp`).

**Interfaces:**
- Produces: `main.dart` loads `PairingStore`; no pairing → `PairScreen`; else `AppShell(TransportManager)` with bottom tabs Remote / Apps / Touchpad and a transport badge in the app bar. `PairScreen`: `mobile_scanner` view; on a decoded QR with `"v":1` → save and go to shell; "Enter code instead" opens a sheet: list of mDNS results (`nsd` discovery of `_egate-remote._tcp`) + 6-digit field → opens a `WsTransport` to the chosen host with `pairCode: code`, which sends `pair {code}` instead of `auth`; on `pair_ok {token,name}` saves the `Pairing` with the returned token.
- Load `frontend-design` skill before writing UI; dark palette, large targets.

- [ ] **Step 1: Implement.** **Step 2: `flutter analyze`** clean. **Step 3: Run on the spare Android phone** (`flutter run -d <id>`): scan the projector's QR → shell shows badge `Wi-Fi`.
- [ ] **Step 4: Commit** `feat(phone): pairing screen and app shell`.

---

### Task 15: Remote screen

**Files:**
- Create: `lib/ui/remote_screen.dart`, `lib/ui/widgets/dpad.dart`, test `test/ui/remote_screen_test.dart` (widget test: tapping OK sends `key 23 tap`; long-press sends `down` then `up`).

**Interfaces:**
- Consumes `TransportManager.send`, `events` (StatusEvent → app name line).
- Keycodes: UP 19, DOWN 20, LEFT 21, RIGHT 22, CENTER 23, BACK 4, HOME 3, MENU 82, VOL_UP 24, VOL_DOWN 25, MUTE 164, PLAY_PAUSE 85, REW 89, FFWD 90, MEDIA_NEXT 87, MEDIA_PREV 88; power row sends `power toggle` / `power sleep` / `power wake` (`wake` hidden if Task 1 recorded "sleep keeps radios on: no"); an "Extra" row with the projector-specific keycodes recorded in `PROTOCOL.md` by Task 1 (omit the row if none).
- D-pad: `GestureDetector` per direction; `onTapDown` → `down`, `onTapUp/onTapCancel` → `up` for OK and volume (press-and-hold), plain `tap` for directions with 120 ms auto-repeat while held. `HapticFeedback.selectionClick()` on every send.

- [ ] **Step 1: Failing widget tests. Step 2: Run** → FAIL. **Step 3: Implement. Step 4: Run** → PASS.
- [ ] **Step 5: Device check:** navigate our launcher and YouTube from the phone.
- [ ] **Step 6: Commit** `feat(phone): remote screen`.

---

### Task 16: Apps tab

**Files:**
- Create: `lib/ui/apps_screen.dart`, `lib/apps/favorites.dart` (shared_preferences list), test `test/ui/apps_screen_test.dart`.

- Grid from `AppsEvent`; icon `CachedNetworkImage('http://$ip:7273/icon/$pkg')` when the manager's kind is wifi or a cached copy exists, else a letter tile; tap → `launch(pkg)`; long-press → toggle favourite; favourites first. Request `apps()` on connect and on pull-to-refresh.

- [ ] **Step 1: Failing widget test** `favourites render first and tap sends launch`. **Steps 2–4** FAIL → implement → PASS.
- [ ] **Step 5: Device check:** launch YouTube and VLC from the phone over Wi-Fi; disable phone Wi-Fi → BLE badge, grid shows letter tiles, launch still works.
- [ ] **Step 6: Commit** `feat(phone): apps tab`.

---

### Task 17: Touchpad + keyboard

**Files:**
- Create: `lib/ui/touchpad_screen.dart`, `lib/ui/keyboard_sheet.dart`, tests `test/ui/touchpad_test.dart`, `test/ui/keyboard_sheet_test.dart`.

- Touchpad: `Listener`/`GestureDetector` pan → `mouse(dx,dy)` with deltas scaled ×1.5, coalesced to at most one message per 16 ms; tap → `click left`; two-finger vertical pan → `scroll(dy)`.
- Keyboard sheet: `TextField` with `autofocus`; `onChanged` diff → send `text` for appended chars, `key 67` (DEL) per removed char; `onSubmitted` → `key 66`; opened from a keyboard icon on the Remote screen.

- [ ] **Step 1: Failing tests** `pan sends coalesced mouse deltas`, `typing abc then backspace sends text a,b,c then key 67`. **Steps 2–4.**
- [ ] **Step 5: Device check:** type "cricket" in YouTube search from the phone; move the pointer in the TV browser.
- [ ] **Step 6: Commit** `feat(phone): touchpad and keyboard`.

---

### Task 18: End-to-end verification + docs

**Files:**
- Modify: spec (append "Build status"), `remote/README.md` (how to build/install both sides, pair, rollback).

- [ ] **Step 1:** Cold reboot projector; within 60 s: BLE advertising, mDNS listed, helper running — no ADB interaction. Record the commands and outputs.
- [ ] **Step 2:** Over Wi-Fi: D-pad in launcher and YouTube, typing in YouTube search, app launch, volume, sleep/wake. Over BLE (phone Wi-Fi off): same list.
- [ ] **Step 3:** 20 rapid D-pad presses per transport; count focus moves (screen recording or `dumpsys` focus) — expected 20/20.
- [ ] **Step 4:** Write README + spec build status; commit `docs: remote build status and readme`.
