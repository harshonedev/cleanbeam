# EGate

Software for the EGate projector: a custom Android TV home screen, a phone remote, and the
device-side tooling that goes with them.

## Layout

```
launcher/   Android TV launcher (Kotlin). Gradle project, open this folder in Android Studio.
remote/     Phone remote. See remote/README.md for pairing and the injector.
  phone/      Flutter app (Android, iOS) with D-pad, apps grid, touchpad, keyboard.
  receiver/   Android service on the projector: WebSocket + BLE server that injects input.
  PROTOCOL.md Wire protocol between phone and receiver.
tools/      Shell and Node scripts used from a laptop over adb (debloat, restore, install remote, push wallpapers).
docs/       Design specs and implementation plans.
```

## Launcher source layout

`launcher/app/src/main/java/com/cleanyourprojector/launcher/`

| Package     | What lives there |
|-------------|------------------|
| (root)      | `LauncherActivity`, the one screen, and `Prefs`. |
| `home`      | The Home tab behind `HomeTab`/`HomeHost`: `CatalogHome` (full flavor, TMDB shelves) or `LiteHome` (lite flavor, clock + favorites + quick settings). |
| `catalog`   | Full flavor only. Streaming content: `Tmdb` client, `JustWatch` deep links, `Services` (known streaming apps), `Title`/`HomeRow` models, `Playback` intent building. |
| `apps`      | Installed apps: `AppInfo` and `AppRepository`. |
| `ui`        | RecyclerView adapters, focus-aware RecyclerViews, the parking layout managers and `SidePanel` (every menu). |
| `graphics`  | Drawing helpers: `Cards`, `Layers`, `Themes` (the built-in looks and `Look`), `Wallpaper`, `ImageLoader`. |
| `settings`  | Settings tab content: `Tile`, `SystemShortcuts`, `DeviceInfo`. |
| `system`    | Manifest components: boot receiver, keep-alive service, clock dream, `RemoteLink` to the receiver. |

Unit tests mirror the package under `launcher/app/src/test/`.

## Build and deploy

```sh
cd launcher
./gradlew :app:assembleFullDebug :app:assembleLiteDebug :app:testFullDebugUnitTest :app:testLiteDebugUnitTest
adb connect <projector-ip>:3333
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk    # or lite/debug/app-lite-debug.apk
```

The Full edition's Home tab needs a TMDB token: either paste it in Settings, or push it to
`/sdcard/Android/data/com.cleanyourprojector.launcher/files/tmdb.key`.

### Your own wallpapers

Both editions show your pictures: the "My wallpaper" theme puts one behind the launcher (a new
one every half hour), and the screensaver puts one behind its clock (a new one every ten minutes),
dimmed by the theme's "Picture dim" setting. Copy them over with

```sh
tools/push-wallpapers.sh [-c] ~/Pictures/some.jpg ...     # -c empties the folder first
```

which lands in `/sdcard/Android/data/com.cleanyourprojector.launcher/files/wallpapers/`.
jpg/png/webp, best at 1920x1080: the box has 1 GB of RAM and decodes each one itself.
To preview the screensaver without waiting: `adb shell service call dreams 1` starts it and
`service call dreams 2` ends it.

### Lite edition

`lite` is the same launcher without the streaming catalogue: no TMDB, no deep links, no image
downloads, and no `INTERNET` permission at all. Its Home tab is a Favorites shelf and a Quick
settings shelf (Wi-Fi, Bluetooth, Focus, Keystone, Brightness, Input source,
Launcher); there is no Favorites tab, and the Apps and Settings tabs are the same as Full. The Ambient theme is Full-only.
Both editions share one package name, so installing one over the other keeps every setting.
Release APKs measured 2026-10-23: Full 1,571 KB, Lite 1,525 KB (dex 901 KB vs 860 KB); the
saving is at runtime (no fetches, image cache, or per-app ambient art), not on disk.
