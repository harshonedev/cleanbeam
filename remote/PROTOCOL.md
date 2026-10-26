# Clean Beam Remote protocol

Single source of truth for both the projector receiver (`remote/receiver`) and the phone app (`remote/phone`).

## Transports

| Transport | Details |
|-----------|---------|
| Wi-Fi | WebSocket `ws://<projector-ip>:7272/`, one JSON object per **text** frame. mDNS service `_cb-remote._tcp`, instance name is the projector's model, port 7272. App icons: `GET http://<projector-ip>:7273/icon/<pkg>` → `image/png` or 404. |
| BLE | GATT server on the projector. The phone finds it by the service UUID below (the adapter name is the projector's own Bluetooth name). Phone requests MTU 512. Each JSON object is UTF-8 encoded then chunked (below). |

### BLE UUIDs

| Item | UUID | Properties |
|------|------|------------|
| Service | `b6517522-6d7b-49d2-8488-664819daea01` | |
| `cmd` characteristic (phone → projector) | `b6517522-6d7b-49d2-8488-664819daea02` | write without response |
| `evt` characteristic (projector → phone) | `b6517522-6d7b-49d2-8488-664819daea03` | notify (CCCD `00002902-0000-1000-8000-00805f9b34fb`) |

### BLE chunk format

Every write / notification carries one chunk:

```
byte 0      : header = (seq & 0x7F) | (last ? 0x80 : 0x00)
bytes 1..n  : payload bytes
```

- `seq` starts at 0 for the first chunk of a message and increments by 1 per chunk, wrapping at 128.
- `last` is set on the final chunk. A message that fits in one chunk is sent as `seq=0, last=1`.
- Max payload per chunk = `mtu - 3 - 1` (ATT header 3 bytes, our header 1 byte). With MTU 23 that is 19 bytes; with 512 it is 508.
- Receiver (`Assembler`): if a chunk's `seq` is not `expected`, discard the partial message and treat the chunk as the start of a new one only if its `seq == 0`; otherwise drop it. Order across messages is guaranteed by the transport, so consecutive single-chunk messages are simply `seq=0,last=1` repeated.

## Messages

JSON object, field `t` selects the type. Unknown fields are ignored. Unknown types get an `err` reply.

### Phone → projector

| `t` | Fields | Notes |
|-----|--------|-------|
| `auth` | `token: string` | Must be the first message on a connection. Reply `auth_ok` or `auth_fail`. Connection closed after 3 failed attempts. |
| `pair` | `code: string` (6 digits) | Alternative first message, only honoured while the projector's pairing screen is open. Reply `pair_ok` (connection is then authenticated) or `pair_fail` (counts as a failed attempt). |
| `key` | `code: int`, `action: "tap"\|"down"\|"up"` | Android `KeyEvent` keycode. `tap` = down+up in one injection. |
| `text` | `s: string` | At most 1000 characters per message (longer → `err`). Characters the virtual keyboard can type become key events; runs of anything else (emoji, Devanagari, accents) are put on the projector's clipboard and pasted with `KEYCODE_PASTE`, so they land in any `TextView`. |
| `mouse` | `dx: int`, `dy: int` | Relative pointer move in pixels. |
| `click` | `button: "left"\|"right"` | Pointer click at the current position. |
| `scroll` | `dy: int` | Vertical scroll, positive = down. |
| `launch` | `pkg: string` | Launch the app's launcher activity. |
| `apps` | | Request the app list. Reply `apps`. |
| `power` | `action: "sleep"\|"wake"\|"reboot"\|"toggle"` | sleep = KEYCODE_SLEEP (223), wake = KEYCODE_WAKEUP (224), toggle = KEYCODE_POWER (26), reboot = `reboot`. |
| `status` | | Reply `status`. |
| `ping` | | Reply `pong`. |
| `shortcuts` | | Request the projector/system screens this box can open. Reply `shortcuts`. |
| `open` | `id: string` | Open one of them (`input`, `wifi`, `bluetooth`, `focus`, `keystone`, `brightness`, `projection`, `projector_settings`, `system_settings`). Silent on success, `err` when unknown or unavailable. |

Examples:

```json
{"t":"auth","token":"Z3VpZC10b2tlbi1iYXNlNjQ="}
{"t":"pair","code":"482913"}
{"t":"key","code":20,"action":"tap"}
{"t":"text","s":"cricket"}
{"t":"mouse","dx":12,"dy":-4}
{"t":"click","button":"left"}
{"t":"scroll","dy":3}
{"t":"launch","pkg":"com.google.android.youtube.tv"}
{"t":"apps"}
{"t":"power","action":"sleep"}
{"t":"status"}
{"t":"ping"}
{"t":"shortcuts"}
{"t":"open","id":"input"}
```

### Projector → phone

| `t` | Fields |
|-----|--------|
| `auth_ok` | `name: string`, `version: string`, `ip: string` (projector's Wi-Fi IPv4; absent when offline) |
| `auth_fail` | |
| `pair_ok` | `token: string`, `name: string` |
| `pair_fail` | |
| `apps` | `items: [{pkg: string, label: string}]` |
| `status` | `app: string\|null` (foreground package), `screenOn: bool`, `volume: int`, `maxVolume: int`, `ip: string` (as in `auth_ok`). Sent on request and pushed on change while a client is connected. A phone on Bluetooth uses `ip` to find the Wi-Fi link. |
| `shortcuts` | `items: [{id: string, label: string, group: string}]` in display order; only entries an installed activity handles. |
| `pong` | |
| `err` | `msg: string` |

Examples:

```json
{"t":"auth_ok","name":"Projector","version":"1.0"}
{"t":"auth_fail"}
{"t":"pair_ok","token":"Z3VpZC10b2tlbi1iYXNlNjQ=","name":"Projector"}
{"t":"apps","items":[{"pkg":"com.google.android.youtube.tv","label":"YouTube"}]}
{"t":"status","app":"com.cleanbeam.launcher","screenOn":true,"volume":7,"maxVolume":15,"ip":"192.168.10.56"}
{"t":"shortcuts","items":[{"id":"input","label":"Input source","group":"Projector"},{"id":"wifi","label":"Wi-Fi","group":"Network"}]}
{"t":"pong"}
{"t":"err","msg":"unknown type: foo"}
```

## Why launches and shortcuts go through the helper

Android refuses `startActivity` from the receiver's foreground service unless the receiver itself has a visible window ("Abort background activity starts"), so `launch` and `open` run `am start` in the injector helper, which is spawned by adbd as shell/root and exempt. `open` starts the resolved activity explicitly (`-n`) with `NEW_TASK|CLEAR_TASK`: some vendor screens (jixin Brightness is one) have no `CATEGORY_DEFAULT`, so an implicit `-a` start cannot resolve them, and those screens share one task, where a plain `NEW_TASK` start piles screens up and never starts the one that is the task's root. Keys are injected with `INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT`: with the async mode some firmwares accept D-pad keys but ignore HOME.

## Pairing QR payload

```json
{"v":1,"name":"Living Room","ip":"192.168.10.52","port":7272,"token":"<base64 of 32 random bytes>"}
```

The phone identifies the projector over BLE by the service UUID (Android apps cannot read the adapter MAC and the advertiser uses a random address).

## Standard keycodes used by the phone UI

| Button | Keycode |
|--------|---------|
| Up / Down / Left / Right | 19 / 20 / 21 / 22 |
| OK (DPAD_CENTER) | 23 |
| Back / Home / Menu | 4 / 3 / 82 |
| Volume up / down / mute | 24 / 25 / 164 |
| Play-pause / Rew / FFwd / Next / Prev | 85 / 89 / 90 / 87 / 88 |
| Enter / Delete | 66 / 67 |
| Power / Sleep / Wakeup | 26 / 223 / 224 |

## Power menu

`{"t":"power","action":"menu"}` is the phone's **Power menu** button.

- On a box with the jixin settings package, the helper taps keycode 135, which is the IR power key on those boards and opens their menu (reboot, screen off, power off).
- On every other Android projector it holds `KEYCODE_POWER` (26) long enough to open Android's own power dialog. A short tap of 26 sleeps the panel, so the phone does not send a short tap.

`power sleep` (`KEYCODE_SLEEP`, 223) and a vendor menu's "Screen Off" put many projectors into full suspend: Wi-Fi drops and nothing the phone sends can wake them. The phone therefore shows no wake button; `power wake` stays in the protocol for a firmware that keeps radios on.
