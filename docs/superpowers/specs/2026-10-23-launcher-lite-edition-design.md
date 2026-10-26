# Projector launcher: Lite edition — Design

**Date:** 2026-10-23
**Scope:** `launcher/` only. A second build of the same launcher that needs no internet.
Brainstorming was skipped at the user's request; the "how" choices below are the builder's calls,
stated so they can be rejected.

## Intent / why
The full launcher's Home tab is a streaming catalogue (TMDB shelves, JustWatch deep links,
backdrop downloads) and its Ambient theme paints the room with per-app art. Some boxes have no
internet, or their owner wants a home screen that is only a clock, their apps and the projector
controls. The Lite edition is that: no catalogue, no network access at all, no Ambient theme.

## Goals
1. One Gradle product flavor per edition, `full` and `lite`, in dimension `edition`. Same
   `applicationId`, so either APK replaces the other in place and the phone remote keeps working.
2. Lite declares no `INTERNET` permission. Nothing in the Lite APK fetches anything.
3. Lite Home tab: a **Favorites** shelf of app cards and a **Quick settings** shelf of tiles,
   with the focused card in the hero exactly as on the other tabs. Apps tab = every app. Settings
   tab = every setting, as in full. No Favorites tab: the favorites are the Home shelf (user
   request, same day).
4. Lite themes: Midnight, Aurora, Nebula, Ruska, Graphite, Pure black, My wallpaper. No Ambient.
   A saved `theme_name` of Ambient falls back to the default (already how `Prefs` behaves).
5. Full keeps today's behaviour exactly; the refactor only moves code.

## Non-goals
- A separate package name or icon for Lite. Only the label differs ("Clean Beam Home Lite").
- Reworking the Settings tab or the side panel.
- Touch, or any new remote behaviour.

## Anti-goals
- Catalogue, TMDB, JustWatch or image-download code in the Lite APK.
- Full's Home tab behaving any differently after the refactor (focus landing, skeletons, hero).

## Decisions
- **Seam:** `object Edition` exists once per flavor (`src/full/java/...`, `src/lite/java/...`)
  with the same shape: `const val NAME`, `const val AMBIENT`, `fun home(host: HomeHost): HomeTab`.
  `const val` is inlined by kotlinc, so `if (Edition.AMBIENT && …)` branches vanish from Lite.
- **Home tab is a class, not activity code.** `home/HomeTab` (abstract) and `home/HomeHost`
  (interface the activity implements) live in `main`. Full's `home/CatalogHome` is today's
  TMDB code lifted out of `LauncherActivity` unchanged in behaviour. Lite's `home/LiteHome`
  shows the clock and the two shelves.
- **What moves to `src/full`:** `catalog/*`, `graphics/ImageLoader`, `ui/HomeRowsAdapter`,
  `ui/TitleAdapter`, `ui/SkeletonAdapter`, `layout/item_title.xml`, `layout/item_skeleton.xml`,
  the Home strings (`home_loading`, `home_no_token*`, `home_empty*`), the catalog unit tests
  (`src/testFull`).
- **What Lite adds:** `home/LiteHome`, `ui/LiteHomeAdapter` (rows of app cards or tiles),
  `src/lite/AndroidManifest.xml` removing `INTERNET`, `src/lite/res/values/strings.xml` with the
  label.
- **Lite Home layout:** same chrome as Full (hero, tabs, status clock); shelves start at the same
  guideline. A large clock in the hero slot was built first and removed the same day at the
  user's request ("same as full").
- **Quick settings shelf:** the Settings tiles titled Wi-Fi, Bluetooth, Focus, Keystone,
  Brightness, Input source, Launcher, in that order, whichever this box resolves. Favorites shelf
  is dropped when there are no favorites (the Apps tab long-press still pins).
- **Ambient gating:** `AppRepository` skips the ambient-art render when `!Edition.AMBIENT`;
  `Themes.ALL` drops dynamic themes when `!Edition.AMBIENT`; the activity's ambient layers are
  built and driven only when `Edition.AMBIENT`.
- **Launcher settings panel:** the Home-specific rows (Refresh Home, Region, TMDB access token)
  come from `HomeTab.settingsRows()`; Lite returns none.
- **Start tab:** Full starts on Home when a TMDB token is set, else Apps (unchanged). Lite always
  starts on Home.

## Left to the builder
- Whether the Quick settings shelf should cap at six tiles; it is at most seven today.
