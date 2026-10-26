# Projector launcher: Outfit font, static themes, side panel — Design

**Date:** 2026-10-23
**Scope:** `launcher/` only. Visual refresh plus removal of technical text. No change to app loading, TMDB shelves, deep links or the remote link.

## Intent / why
The launcher looks like a generic dark TV UI: system font, four stacked full-screen gradients, per-app ambient art and a colour tint, and stock `AlertDialog` menus popping in the middle of the screen. The reference is [cleanyourtv](https://github.com/Gathin23/cleanyourtv): one still background per theme, Outfit type, frosted glass where a wallpaper is behind it. Users also see strings meant for developers (versions, "last opened", RAM, IP, firmware build).

## Goals
1. Outfit everywhere (hero, tabs, cards, dream clock, panel).
2. A `Theme` list the user picks from Settings: each is one background (flat colour or one bundled picture) and a card style (solid or glass). Picking a theme previews live behind the panel.
3. No shaders at draw time: no gradient drawables, no tint mask, no scrims. The only per-frame work is the wallpaper bitmap and flat colours.
4. Every menu is a side panel that slides in from the right, stacks levels, and collapses with Back (one level) or Left (all).
5. Drop version names, "Last opened …", usage-stats, RAM/storage line, IP address, Android/firmware strings.

## Non-goals
- Real blur (RenderEffect) — too costly on the Mali-450; glass is a translucent fill with a rim.
- cleanyourtv's `config.json` theme push. Themes are built in; "My wallpaper" keeps the pushed-images feature.
- Touch gestures (swipe to dismiss). D-pad only.

## Anti-goals
- A frame-rate regression on the Apps grid or Home shelves.
- Focus escaping the panel to a tab (which would switch tabs behind it).

## Decisions
- **Font:** `res/font/outfit.ttf` (variable, `wght` 100–900), one `font-family` XML per weight (200, 300, 400, 500, 600, 700) so the variable instance is built once per process, not per view. Theme sets `android:textViewStyle`/`editTextStyle` to Outfit regular; layouts name the weight.
- **Themes** (`graphics/Themes.kt`): `Theme(name, background, wallpaper, glass, userWallpaper)`.
  Midnight `#070A14` solid · Aurora (`wp_aurora`, glass) · Nebula (`wp_nebula`, glass) · Ruska (`wp_ruska`, glass) · Graphite `#15171C` glass · Pure black `#000000` solid · My wallpaper (files/wallpapers, rotates every 30 min, glass).
  Wallpapers come from cleanyourtv's `themes/*/wallpaper.jpg` (MIT), resized to 1280×720 (the box's override size), decoded RGB_565, in `drawable-nodpi`.
- **Dim:** a flat black `View` over the picture, alpha = `wallpaperDim`% (0–80, default 20). Not shown on flat themes.
- **Glass:** `card_surface_glass` = `#26FFFFFF` fill, 1dp `#2EFFFFFF` rim, 14dp corners. Panel glass = `#CC0A0E1A` + `#30FFFFFF` rim. `Look.glass` is the one global flag; adapters read `Look.cardBg` on bind so recycled cards pick up a theme change; live cards are restyled by walking the tree.
- **Side panel** (`ui/SidePanel.kt`, 380dp, right edge): `open { Content(title, rows, message?, input?, onKey?) }` pushes a level; `refresh()` rebuilds the top level keeping the focused row (toggles); `back()` pops, closing when empty; `close()` collapses. Slide-in 360 ms / out 260 ms with `PathInterpolator(0.2,0,0,1)`, scrim fade; level swaps slide 16dp. `focusSearch` keeps focus inside; `dispatchKeyEvent` handles Back/Left and a per-level key interceptor (shortcut capture).
- **Hero text:** apps show label + category ("Video app"); titles show kind, year, "rated 7.8"; settings tiles show group + detail. `hero_meta` is gone.
- **Removed code:** ambient art (`AppInfo.ambient`), tint mask, scrims, `Sky`, stats line, usage stats (`PACKAGE_USAGE_STATS`), `AppInfo.version/installTime/lastUsed/foregroundMs`, `DeviceInfo.system()/firmware()`, the dialog theme.

## Left to the builder
- Exact alphas if glass reads too bright on Ruska's sky.
- Whether to cache decoded wallpapers across theme switches (currently decoded on change, ~1.8 MB each).

## Found while building
- **Grid anchor behind the panel.** With a menu holding focus, a relayout of the Apps/Settings grid (theme change restyles every card) re-anchored at a row above the parked one, because rows laid out above the top for D-pad search count as "visible" to `GridLayoutManager`, and the old 400dp bottom padding put every row out of bounds. `ParkingGridLayoutManager.onLayoutChildren` now pins the anchor to the row at the top when nothing in the grid has focus and no scroll was requested; `FocusGridRecyclerView` sizes its bottom padding to "height minus one row slot". The old `AlertDialog`s never showed this because they lived in another window and the grid kept its focused child.
- **Down from the last column.** With a shorter last row, Down from the last column did nothing, so OK opened that tile (the HDMI input) instead of reaching Launcher. `FocusGridRecyclerView` now lands on the last tile.
- **Pushed files are shell-owned.** `adb push` into the app's external files dir leaves the file owned by `shell`, and the app gets EACCES (the TMDB key read as "Not set"). Fixed on the box with `chown` as root; the launcher code is unchanged.
- **Flat theme after a picture theme kept the picture.** `applyTheme` reset the background key before `applyWallpaper` ran, so the "clear" branch never fired; only a restart showed the flat theme. The key now lives in `graphics/Backdrop` (private, with a unit test for load / no-op / clear), and the activity only asks it what changed.
- **From the code review, also fixed:** the dim view is drawn without an offscreen layer (`forceHasOverlappingRendering(false)`); the panel builds a level's new rows beside the old ones and hands focus across before removing them (removing the focused row first sent focus to the Home tab for a frame); the first slide-in uses the sheet's fixed layout width instead of waiting for a measure; the grid anchor pin runs on every focus-less layout (RecyclerView drops the mid-layout requestLayout, so no guard is needed); cards restyle on attach as well as bind so cached holders follow a theme change; the user wallpaper folder is listed once per half hour, not every minute. Not changed: with usage stats gone, two installed apps for one service resolve in label order instead of most-recent (usage stats were empty on this box anyway).

## Added later the same day
- **Ambient theme** (user request): the previous look back as one theme. `Theme.dynamic`: the focused app's tiny blurred art (`AppInfo.ambient`) crossfades between two full-screen layers, a baked tint mask takes the app's colour, the root is the time-of-day `Sky` gradient (re-baked when the hour changes), and the left/bottom scrims return; titles fetch their TMDB backdrop for the room. All gradients are still bitmaps (`Layers`), so the "no per-frame shader" rule holds; the cost is the two art layers' alpha fades on focus change, as before. Flat and picture themes clear the art and tint when selected.
- **Theme saved by name** (`theme_name`), so inserting Ambient did not move anyone's choice; an old integer `theme` is read once as a fallback.
- **Left/Right in the grid stay on their row**: at the end of a short last row the nearest view to the right was one row up, so Right climbed instead of stopping.
