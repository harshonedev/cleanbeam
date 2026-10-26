# Prompts for AI coding assistants

Clean Beam works well with an AI coding assistant that can run shell commands: **Claude Code**,
**Codex CLI**, **Gemini CLI**, Cursor or Aider. The assistant discovers your projector's bloat,
asks you before touching anything you might use, runs this repo's scripts, and checks each step with
screenshots pulled from the projector.

**Before you paste a prompt:**

1. Turn on Developer options and USB / Network debugging on the projector
   ([how](docs/guides/02-connect-your-projector.md)).
2. Clone the repo and open **that folder** in your assistant:
   `git clone https://github.com/harshonedev/cleanbeam && cd cleanbeam`.
   The assistant then reuses `tools/` and reads [`AGENTS.md`](AGENTS.md), where the safety rules live.
3. Replace everything in `<ANGLE BRACKETS>`.

| Prompt | Use it to |
|---|---|
| [1. Full setup](#1-full-setup) | Do everything: debloat, launcher, screensaver, phone remote |
| [2. Debloat only](#2-debloat-only) | Clean the firmware, keep the stock launcher |
| [3. Launcher only](#3-launcher-only) | Install the launcher, pick a theme, set wallpapers and the catalogue |
| [4. Phone remote](#4-phone-remote) | Receiver on the projector, app on your phone, pairing |
| [5. Add my projector to Clean Beam](#5-add-my-projector-to-clean-beam) | Turn your result into a `models/` folder and a pull request |
| [6. Restore](#6-restore) | Undo everything |
| [7. Troubleshoot](#7-troubleshoot) | Something broke |
| [8. Developer prompts](#8-developer-prompts) | Add a theme, a streaming service, or support for a projector's controls |

---

## 1. Full setup

```text
I want to clean up my Android projector with Clean Beam (this repo): debloat it, install the
Clean Beam launcher as Home, set its clock screensaver, and set up the phone remote.

My projector: <BRAND AND MODEL, e.g. "Wanbo X5 Pro">
Its IP address, if I know it: <IP or "find it">
My computer: <macOS / Linux / Windows with Git Bash>
Launcher edition: <Full (streaming catalogue) / Lite (no internet, lighter)>
My phone for the remote: <Android, connected over USB / I'll install the APK myself / skip the remote>

Read AGENTS.md first and follow it. Work on the real device, step by step, and check every step
with a screenshot from the projector (adb exec-out screencap -p). Ask me before anything that
depends on my preferences.

1. Connect: find the projector (adb mdns services, or the IP I gave), and adb connect on 5555, then
   3333. If both are refused, walk me through Wireless debugging pairing.
2. Scan: run tools/scan.sh. If models/ already has my projector, show me its list. If not, sort the
   enabled system packages into: telemetry/analytics, ad and content pushers, app stores, remote
   installers, update/OTA, vendor screensaver, preloaded apps, streaming apps, and "firmware
   plumbing - keep". Use dumpsys package on anything unclear.
3. Ask me with a checklist which preloaded apps, streaming apps, screen mirroring, voice assistant and
   update service I use. Then write models/<brand>-<model>/debloat.txt with a comment per group and a
   "Kept on purpose" header.
4. Launcher: build (cd launcher && ./gradlew :app:assemble<Full|Lite>Debug) or use a release APK,
   install it, make it Home (check getprop | grep -i launcher for a vendor Home property), grant
   GET_USAGE_STATS and WRITE_SECURE_SETTINGS, set the clock screensaver, and screenshot Home.
5. Debloat: DRY_RUN=1 tools/debloat.sh <list> first, show me, then run it for real. Reboot and check
   HDMI, focus, keystone, Wi-Fi, Bluetooth, the IR remote and sound. If anything broke, run
   tools/restore.sh and redo it in smaller batches.
6. Phone remote: check ro.adb.secure (0 or empty means it will work), run
   tools/install-remote.sh --build, build and install the phone app (remote/phone, cd android &&
   ./gradlew assembleDebug), and help me pair with the 6-digit code.
7. Full edition: help me get a free TMDB read access token and push it to
   /sdcard/Android/data/com.cleanbeam.launcher/files/tmdb.key (never commit it), and set my region.
8. Finish: show me before/after screenshots, tell me how to restore, and ask whether to switch
   network ADB off (only if I'm not using the phone remote). Offer to prepare a pull request with my
   models/ folder.
```

## 2. Debloat only

```text
Debloat my Android projector with this repo's tools, without changing its launcher.

Projector: <BRAND AND MODEL>   IP: <IP or "find it">

Read AGENTS.md and follow it. Connect over adb (5555, then 3333), run tools/scan.sh, and use
models/<brand>-<model>/debloat.txt if it exists. Otherwise build one: sort the enabled system
packages into telemetry, ad/content pushers, app stores, remote installers, OTA, vendor screensaver,
preloaded apps and firmware plumbing to keep. Use dumpsys package on anything unclear, and ask me
with a checklist about anything I might use. Never put the current Home app in the list.
Run DRY_RUN=1 tools/debloat.sh <list>, show me the result, then run it for real. Reboot and check
with screenshots that HDMI, focus, keystone, Wi-Fi, Bluetooth, the remote and sound still work.
Tell me how to undo it (tools/restore.sh).
```

## 3. Launcher only

```text
Install the Clean Beam launcher on my projector and set it up.

Projector: <BRAND AND MODEL>   IP: <IP>   Edition: <Full / Lite>
Theme I'd like: <Midnight / Ambient (Full only) / Aurora / Nebula / Ruska / Graphite / Pure black / My wallpaper>
Wallpapers to push (for "My wallpaper" and the screensaver): <paths on my computer, or "none">
Favourite apps, in order: <e.g. YouTube, Netflix, Prime Video, VLC>

Read AGENTS.md. Build or install the launcher (docs/guides/04-launcher.md), make it Home (and
handle any vendor Home property: check getprop | grep -i launcher), then grant GET_USAGE_STATS and
WRITE_SECURE_SETTINGS. Set the clock screensaver, and push my wallpapers with
tools/push-wallpapers.sh. Tell me which remote buttons pick the theme and pin my favourites, or do it
over adb input keyevent, checking each step with a screenshot. For Full, walk me through getting a
TMDB read access token and push it to files/tmdb.key. Show me a screenshot of the result.
```

## 4. Phone remote

```text
Set up the Clean Beam phone remote.

Projector: <BRAND AND MODEL>   IP: <IP>
Phone: <Android model>, <connected over USB with USB debugging on / not connected>

Read AGENTS.md and remote/README.md. Check the projector's ro.adb.secure and adb port first, and
tell me plainly if key injection can't work on it. Install the receiver with
tools/install-remote.sh --build (set ANDROID_SERIAL to the projector). Build the phone app
(cd remote/phone/android && ./gradlew assembleDebug) and install it on my phone (or give me the
APK path). Then guide me through pairing with the QR code or the 6-digit code on the projector.
Confirm it works: the badge shows Wi-Fi, and the projector's launcher shows "Phone remote
connected".
```

## 5. Add my projector to Clean Beam

```text
I've debloated my projector and want to contribute it to Clean Beam.

Projector: <BRAND AND MODEL>   IP: <IP>

Read CONTRIBUTING.md and AGENTS.md. Run tools/scan.sh. Create models/<brand>-<model>/ with:
- debloat.txt: the packages I disabled, grouped with a comment each, a header naming the board,
  Android version and adb port, and a "Kept on purpose" section listing the projector controls, the
  input layer and anything else that must stay. Mark anything not tested on my hardware with
  "# untested:".
- device.json: same fields as models/egate-atom-3x/device.json.
- README.md: same sections as models/egate-atom-3x/README.md. Include how Home is chosen on this
  firmware, the screensaver behaviour, where focus and keystone live, and any quirks.
Add a row to the "Supported projectors" table in README.md. Don't include my IP, Wi-Fi name or
serials anywhere. Then create a branch, commit, and show me the pull request text before pushing.
```

## 6. Restore

```text
Undo Clean Beam on my projector. Projector IP: <IP>.
Read docs/guides/06-restore.md. Run tools/restore.sh (or, if backups/ is empty,
tools/restore.sh --list models/<model>/debloat.txt). Make sure the stock launcher is Home again,
including any vendor Home property from the model's README. Then ask me whether to uninstall
com.cleanbeam.launcher and com.cleanbeam.remote. Confirm with a screenshot.
```

## 7. Troubleshoot

```text
Something is wrong with my projector after using Clean Beam.

Projector: <BRAND AND MODEL>   IP: <IP or "can't connect">
What happened: <e.g. "black screen after reboot", "HDMI says No signal", "Home goes back to the old launcher">
What I ran: <commands or prompt>

Read docs/guides/troubleshooting.md and AGENTS.md. Diagnose before changing anything: connect, take
a screenshot, check the Home activity, pm list packages -d, and the newest backups/ folder. Explain
the cause, propose the smallest fix, and wait for my OK. Prefer re-enabling packages over anything
else. Never uninstall, never adb root.
```

## 8. Developer prompts

**Add a theme**

```text
Add a theme called <NAME> to the Clean Beam launcher: <describe the look: colours, picture or
flat, glass or solid cards>. Follow how Themes.kt defines the existing ones (a bundled picture goes
in res/drawable-nodpi, about 1920x1080, under 200 KB). It must work in both full and lite flavors.
Run ./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest. If a projector is connected,
install it, switch to the theme, and show me a screenshot.
```

**Add a streaming service to the catalogue**

```text
Add <SERVICE> to the Clean Beam catalogue. Find its Android TV package name, its JustWatch
provider name(s) and a search URL, then add it to
launcher/app/src/full/java/com/cleanbeam/launcher/catalog/Services.kt like the others. Add a unit
test next to the existing catalogue tests and run the full flavor tests.
```

**Support a projector's focus / keystone screens**

```text
On my projector (<MODEL>, connected over adb), find the activities that open focus, keystone,
picture/brightness, projection mode and input source (dumpsys package, look for vendor intents),
and add them to the launcher's VendorScreens and to the remote receiver's shortcut list so both
show them. Verify each one opens with adb shell am start, then show me screenshots.
```
