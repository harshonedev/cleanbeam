# Launcher Lite Edition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `lite` product flavor of the launcher with no catalogue, no `INTERNET` permission and no Ambient theme, whose Home tab is a large clock, a Favorites shelf and a Quick settings shelf; the `full` flavor keeps today's behaviour.

**Architecture:** Two Gradle flavors in dimension `edition` share `src/main`. A per-flavor `object Edition` is the only seam: it names the edition, says whether Ambient exists, and builds the edition's `HomeTab`. The Home tab's code leaves `LauncherActivity` for `home/CatalogHome` (full, today's TMDB code moved) and `home/LiteHome` (lite); the activity implements `home/HomeHost` for both.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, minSdk 28, compileSdk 34, RecyclerView, JUnit 4. Build-tools 34.0.0 `aapt` for the permission check.

**Spec:** `docs/superpowers/specs/2026-10-23-launcher-lite-edition-design.md`

## Global Constraints

- Same `applicationId` (`com.cleanyourprojector.launcher`) for both flavors; Lite differs only by label `EGate Home Lite` and `versionNameSuffix "-lite"`.
- Lite APK declares no `android.permission.INTERNET`.
- Full's behaviour is unchanged: same start tab rule, same shelves, skeletons, focus landing, hero, settings rows.
- Lite themes, in order: Midnight, Aurora, Nebula, Ruska, Graphite, Pure black, My wallpaper. `Themes.DEFAULT` stays 0.
- Lite Home: hero and the top-right status clock hidden; clock block in the hero slot (time 96sp Outfit light, AM/PM 28sp, date 20sp `EEEE, d MMMM`); shelves **Favorites** (dropped when empty) then **Quick settings**.
- Quick settings tiles, by Settings-tile title, in this order, only those the box resolves: Wi-Fi, Bluetooth, Focus, Keystone, Brightness, Input source, Launcher.
- No per-frame shaders, nothing new fetched over the network, no new permissions.
- Gradle tasks per flavor: `:app:assembleFullDebug`, `:app:assembleLiteDebug`, `:app:testFullDebugUnitTest`, `:app:testLiteDebugUnitTest`. Run from `launcher/`.
- Commits: no `Co-Authored-By` trailer, no "Generated with Claude Code" line. Work on branch `remote`.

## Review Focus

1. A box that saved `theme_name = Ambient` in Full, then gets Lite installed over it: Lite must start on Midnight, not crash or index past the list — Task 2 test `an Ambient choice saved by Full falls back to the default`.
2. Lite Home with no favorites at all (a fresh box with none of the seeded streaming apps): Home must still show the Quick settings shelf and land focus on its first tile — Task 4 test `no favorites leaves only the quick settings shelf`, Task 6 device check.
3. HOME key pressed while Lite Home is already showing and focus is deep in the Quick settings shelf: the shelves must scroll to the top and focus the first card — Task 4 `scrollToTop`, Task 6 device check.
4. Pin or unpin from the Apps tab, then return to Home: the Favorites shelf must reflect it without losing the focused card — Task 4 `onFavoritesChanged`, Task 6 device check.
5. The clock at midnight and at noon in 12-hour mode ("12:00 AM" / "12:00 PM"), and the date line's weekday — Task 5 tests `twelve hour clock at midnight and noon`.

---

### Task 1: Commit the pending themes/panel work

The working tree holds the finished Outfit/themes/side-panel feature (spec `docs/superpowers/specs/2026-10-23-launcher-themes-panel-design.md`), uncommitted. It built and passed `:app:assembleDebug :app:testDebugUnitTest` on 2026-10-23. Commit it on its own so the Lite work is a separate history.

**Files:**
- Everything `git status` lists under `launcher/`, `README.md`, `docs/superpowers/specs/2026-10-23-launcher-themes-panel-design.md`, `launcher/FONT-LICENSE-OFL.txt`. Not the Lite spec or this plan.

- [ ] **Step 1: Confirm the tree still builds**

Run: `cd launcher && ./gradlew :app:assembleDebug :app:testDebugUnitTest -q`
Expected: exit 0, no output.

- [ ] **Step 2: Commit**

```bash
git add README.md launcher docs/superpowers/specs/2026-10-23-launcher-themes-panel-design.md
git commit -m "feat(launcher): Outfit font, static themes, side panel menus"
```

- [ ] **Step 3: Commit the Lite spec and this plan**

```bash
git add docs/superpowers/specs/2026-10-23-launcher-lite-edition-design.md docs/superpowers/plans/2026-10-23-launcher-lite-edition.md
git commit -m "docs(launcher): lite edition design and plan"
```

---

### Task 2: Flavors, `Edition`, theme list and ambient-art gating

**Files:**
- Modify: `launcher/app/build.gradle.kts` (inside `android {}`)
- Create: `launcher/app/src/full/java/com/cleanyourprojector/launcher/Edition.kt`
- Create: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/Edition.kt`
- Create: `launcher/app/src/lite/AndroidManifest.xml`
- Create: `launcher/app/src/lite/res/values/strings.xml`
- Modify: `launcher/app/src/main/java/com/cleanyourprojector/launcher/graphics/Themes.kt:28-37`
- Modify: `launcher/app/src/main/java/com/cleanyourprojector/launcher/apps/AppRepository.kt:62`
- Modify: `launcher/app/src/test/java/com/cleanyourprojector/launcher/graphics/ThemesTest.kt` (remove the dynamic-theme test)
- Create: `launcher/app/src/testFull/java/com/cleanyourprojector/launcher/EditionTest.kt`
- Create: `launcher/app/src/testLite/java/com/cleanyourprojector/launcher/EditionTest.kt`

**Interfaces:**
- Produces: `object Edition { const val NAME: String; const val AMBIENT: Boolean }` in package `com.cleanyourprojector.launcher`, one per flavor (`"full"`/`true`, `"lite"`/`false`). Task 3 adds `fun home(host: HomeHost): HomeTab` to both.
- Produces: `Themes.ALL` filtered by `Edition.AMBIENT || !it.dynamic`.

- [ ] **Step 1: Write the failing flavor tests**

`src/testFull/.../EditionTest.kt`:
```kotlin
class EditionTest {
    @Test fun `full has the ambient theme`() {
        assertTrue(Edition.AMBIENT)
        assertEquals("full", Edition.NAME)
        assertEquals(1, Themes.ALL.count { it.dynamic })
        assertEquals("Ambient", Themes.ALL[1].name)
    }
}
```
`src/testLite/.../EditionTest.kt`:
```kotlin
class EditionTest {
    @Test fun `lite has no ambient theme`() {
        assertFalse(Edition.AMBIENT)
        assertEquals("lite", Edition.NAME)
        assertEquals(listOf("Midnight", "Aurora", "Nebula", "Ruska", "Graphite", "Pure black", "My wallpaper"),
            Themes.ALL.map { it.name })
    }
    @Test fun `an Ambient choice saved by Full falls back to the default`() {
        assertEquals(-1, Themes.indexOf("Ambient"))
        assertEquals("Midnight", Themes.ALL[Themes.DEFAULT].name)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testLiteDebugUnitTest -q`
Expected: FAIL — `Task ':app:testLiteDebugUnitTest' not found` (no flavors yet).

- [ ] **Step 3: Add the flavors**

In `build.gradle.kts` inside `android {}`, after `defaultConfig`:
```kotlin
flavorDimensions += "edition"
productFlavors {
    create("full") { dimension = "edition"; isDefault = true }
    create("lite") { dimension = "edition"; versionNameSuffix = "-lite" }
}
```

- [ ] **Step 4: Add the two `Edition` objects**

Full: `NAME = "full"`, `AMBIENT = true`. Lite: `NAME = "lite"`, `AMBIENT = false`. KDoc on each: "The one place a flavor differs in code; `const val` so dead branches drop out of the other edition."

- [ ] **Step 5: Lite manifest and label**

`src/lite/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
</manifest>
```
`src/lite/res/values/strings.xml`: `app_name` = `EGate Home Lite`.

- [ ] **Step 6: Filter the theme list and skip ambient art**

`Themes.ALL`: keep the eight entries, append `.filter { Edition.AMBIENT || !it.dynamic }`.
`AppRepository.build`: `ambient = if (Edition.AMBIENT) ambientArt(banner ?: icon) else null`.
`ThemesTest`: delete `the one dynamic theme has neither picture nor glass` (now per flavor in `EditionTest`).

- [ ] **Step 7: Run both flavors' tests and builds**

Run: `./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:assembleFullDebug :app:assembleLiteDebug -q`
Expected: exit 0.

- [ ] **Step 8: Check the permissions**

Run: `A=$ANDROID_HOME/build-tools/34.0.0/aapt; $A dump permissions app/build/outputs/apk/lite/debug/app-lite-debug.apk | grep -c INTERNET; $A dump permissions app/build/outputs/apk/full/debug/app-full-debug.apk | grep -c INTERNET`
Expected: `0` then `1`.

- [ ] **Step 9: Commit**

```bash
git add launcher
git commit -m "build(launcher): full and lite product flavors; lite drops Ambient and INTERNET"
```

---

### Task 3: The `HomeTab` seam; Full's Home tab moves out of the activity

Behaviour-preserving extraction. Every method named below is today's code in `LauncherActivity` moved as-is, with `this`/fields replaced by `host` calls.

**Files:**
- Create: `launcher/app/src/main/java/com/cleanyourprojector/launcher/home/HomeTab.kt` (`HomeHost` + `HomeTab`)
- Create: `launcher/app/src/full/java/com/cleanyourprojector/launcher/home/CatalogHome.kt`
- Create: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/home/LiteHome.kt` (stub; Task 4 fills it)
- Modify: both `Edition.kt` — add `fun home(host: HomeHost): HomeTab`
- Modify: `launcher/app/src/main/java/com/cleanyourprojector/launcher/LauncherActivity.kt`
- Move (git mv) to `src/full/java/com/cleanyourprojector/launcher/…`: `catalog/*.kt`, `graphics/ImageLoader.kt`, `ui/HomeRowsAdapter.kt`, `ui/TitleAdapter.kt`, `ui/SkeletonAdapter.kt`
- Move to `src/full/res/layout/`: `item_title.xml`, `item_skeleton.xml`
- Create: `src/full/res/values/strings.xml` with `home_loading`, `home_no_token`, `home_no_token_hint`, `home_empty`, `home_empty_hint` (cut from `src/main/res/values/strings.xml`)
- Move: `src/test/java/.../catalog/*.kt` → `src/testFull/java/.../catalog/`

**Interfaces:**
- Produces, in `main`:
```kotlin
package com.cleanyourprojector.launcher.home

/** What the activity lends the Home tab. The activity implements it; the edition's HomeTab uses it. */
interface HomeHost {
    val activity: Activity
    val prefs: Prefs
    val shelves: ShelvesRecyclerView
    val homeTabView: View                 // D-pad up from the first shelf
    val panel: SidePanel
    val heroItem: Any?
    val isHomeShowing: Boolean
    fun apps(): List<AppInfo>
    fun favoriteApps(): List<AppInfo>
    fun settingsTiles(): List<Tile>       // the Settings tab's tiles, actions wired
    fun launchApp(app: AppInfo)
    fun appMenu(app: AppInfo)
    fun onCardFocused(item: Any)
    fun setHero(title: String, subtitle: String, body: String?)   // animated swap; blank body hides the line
    fun paintRoom(art: Bitmap?, color: Int)                       // Ambient only; no-op otherwise
    fun setHeroVisible(visible: Boolean)
    fun setStatusClockVisible(visible: Boolean)
    fun showEmpty(title: Int, hint: Int)
    fun hideEmpty()
    fun showHome()                        // select the Home tab, or redraw it if selected
    fun toast(message: String)
    fun safeStart(intent: Intent)
}

abstract class HomeTab(protected val host: HomeHost) {
    abstract val startsOnHome: Boolean
    abstract val loadingText: Int
    open fun onCreate() {}
    abstract fun show()
    open fun hide() {}
    abstract fun scrollToTop()
    abstract fun onFavoritesChanged()
    open fun renderHero(item: Any): Boolean = false
    open fun settingsRows(): List<SidePanel.Row> = emptyList()
    open fun onTick(now: Date) {}
    open fun onDestroy() {}
}
```
- Produces, in `full`: `class CatalogHome(host: HomeHost) : HomeTab(host)`; in `lite`: `class LiteHome(host: HomeHost) : HomeTab(host)`.
- Consumes: `Edition` from Task 2.

- [ ] **Step 1: Write the seam** — `HomeTab.kt` exactly as above, KDoc per member as in the comments.

- [ ] **Step 2: `CatalogHome`** — move from the activity:
  - fields `shelvesAdapter`, `homeRows`, `homeLoadedAt`, `homeLoading`, `homeGen`, `homeFirstShow`; own `io` (single thread), `rowsIo` (3 threads), `handler`; `region get() = host.prefs.region.ifBlank { Tmdb.defaultRegion() }`.
  - `startsOnHome = Tmdb.configured(host.activity)`, `loadingText = R.string.home_loading`.
  - `onCreate()`: build `HomeRowsAdapter` with the six callbacks (`openTitle`, `titleMenu`, `{ t, r -> host.onCardFocused(t to r) }`, `host::launchApp`, `host::appMenu`, `host::onCardFocused`), `upTarget = host.homeTabView`, set it on `host.shelves`, `itemAnimator = null`.
  - `show()` = `populateHome` minus the `row`/`grid` hiding (the activity does that); `renderHome`, `loadHome`, `showShelves`, `focusFirstShelfCard`, `displayRows` (header from `host.activity.getString(R.string.tab_favorites)`), `firstCard`.
  - `scrollToTop()` = `host.shelves.scrollToPosition(0); focusFirstShelfCard()`.
  - `onFavoritesChanged()` = `shelvesAdapter.submit(displayRows())`.
  - `renderHero(item)`: `val t = (item as? Pair<*, *>)?.first as? Title ?: return false`; then today's `renderTitleHero` body with `host.setHero(t.name, subtitle, t.overview)`; the backdrop fetch runs only when `Edition.AMBIENT && host.prefs.theme.dynamic` and ends in `host.paintRoom(res.first, res.second)`, guarded as today by `(host.heroItem as? Pair<*, *>)?.first != t`.
  - `settingsRows()`: Refresh Home (clears caches, `homeRows = emptyList(); homeLoadedAt = 0; host.panel.close(); host.showHome()`), Region → `pickRegion()`, TMDB access token → `enterToken()` (its Save ends in `host.panel.close(); host.showHome()`).
  - `openTitle`, `titleMenu`, `watchOptions`, `chooseWatchOption`, `watch`, `WatchOption` unchanged, `toast`/`safeStart` via host.
  - `onDestroy()` shuts both executors down.

- [ ] **Step 3: `LiteHome` stub** — `startsOnHome = true`, `loadingText = R.string.loading`, `show()` = `host.showEmpty(R.string.empty_favorites, R.string.empty_favorites_hint)`, `scrollToTop()`/`onFavoritesChanged()` empty. Both `Edition.home()` return their class.

- [ ] **Step 4: Rewire `LauncherActivity`**
  - `class LauncherActivity : AppCompatActivity(), HomeHost`; `private lateinit var home: HomeTab`, created right after the adapters in `onCreate` (`home = Edition.home(this); home.onCreate()`), before `tab = if (home.startsOnHome) Tab.HOME else Tab.APPS`.
  - Delete the fields and methods that moved (`shelvesAdapter`, `homeRows`…, `rowsIo`, `region`, `populateHome`, `renderHome`, `loadHome`, `showShelves`, `focusFirstShelfCard`, `displayRows`, `openTitle`, `titleMenu`, `watchOptions`, `chooseWatchOption`, `watch`, `WatchOption`, `renderTitleHero`, `pickRegion`, `enterToken`, the Refresh Home / Region / TMDB rows) and the `catalog`/`ImageLoader`/`HomeRowsAdapter` imports.
  - `heroTitle.text = getString(if (tab == Tab.HOME) home.loadingText else R.string.loading)`.
  - `heroRunnable`: `is AppInfo`, `is Tile`, `else -> home.renderHero(it)`.
  - `populate()`: `Tab.HOME -> { row.visibility = GONE; grid.visibility = GONE; home.show() }`; the other three branches call `home.hide()` first.
  - `onNewIntent` HOME → `home.scrollToTop()`; `refreshIfFavorites` HOME → `home.onFavoritesChanged()`; `showLauncherOptions`: after the Remote shortcuts row, `rows += home.settingsRows()`; `updateClock`: `home.onTick(now)` after the status text; `onDestroy`: `home.onDestroy()`.
  - `HomeHost` members: `activity = this`, `homeTabView = tabHome`, `isHomeShowing = tab == Tab.HOME`, `apps() = allApps`, `settingsTiles() = tiles()`, `setHero` = `swapHero(animate = true) { … }` setting title/subtitle/body (GONE when blank), `paintRoom` = `if (!(Edition.AMBIENT && prefs.theme.dynamic)) return; crossfadeArt(art?.let { BitmapDrawable(resources, it).apply { alpha = 0x99 } }); animateTint(color)`, `setHeroVisible` → `hero.visibility` VISIBLE/GONE, `setStatusClockVisible` → `clock.visibility` VISIBLE/GONE, `hideEmpty` → `empty.visibility = GONE`, `showHome` → `if (tab == Tab.HOME) populate(animate = false) else selectTab(Tab.HOME)`; `favoriteApps`, `launchApp`, `appMenu`, `onCardFocused`, `showEmpty`, `toast`, `safeStart` become `override` (public).

- [ ] **Step 5: Move the files and strings** (git mv list above). `README.md` source-layout table: `catalog` row notes "(full flavor only)"; add a `home` row.

- [ ] **Step 6: Build and test both flavors**

Run: `./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:assembleFullDebug :app:assembleLiteDebug -q`
Expected: exit 0. Then `grep -rl "Tmdb\|JustWatch\|ImageLoader" app/src/main` prints nothing.

- [ ] **Step 7: Full on the box, unchanged**

Run: `export ANDROID_SERIAL=192.168.10.52:3333; adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk && adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME && sleep 6 && adb exec-out screencap -p > /private/tmp/claude-501/-Users-harsh-Public-Projects-e-gate/f02477ce-fd04-4550-9f6f-05e4fca585f1/scratchpad/full-home.png`
Expected: Home shelves (Favorites + TMDB rows) as before; `adb logcat -d -s Launcher | tail` shows `home rows: …`. If the box is unreachable, say so and continue; do not skip Step 6.

- [ ] **Step 8: Commit**

```bash
git add -A launcher README.md
git commit -m "refactor(launcher): Home tab behind a per-edition HomeTab; catalogue code to the full flavor"
```

---

### Task 4: Lite Home shelves (Favorites + Quick settings)

**Files:**
- Create: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/home/LiteRows.kt`
- Create: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/ui/LiteHomeAdapter.kt`
- Modify: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/home/LiteHome.kt`
- Create: `launcher/app/src/lite/res/values/strings.xml` — add `home_quick` = `Quick settings`
- Test: `launcher/app/src/testLite/java/com/cleanyourprojector/launcher/home/LiteRowsTest.kt`

**Interfaces:**
- Produces:
```kotlin
package com.cleanyourprojector.launcher.home
data class LiteRow(val header: String, val apps: List<AppInfo> = emptyList(), val tiles: List<Tile> = emptyList())
object LiteRows {
    val QUICK: List<String>   // "Wi-Fi", "Bluetooth", "Focus", "Keystone", "Brightness", "Input source", "Launcher"
    fun quick(tiles: List<Tile>): List<Tile>                 // QUICK order, only titles present
    fun build(favorites: List<AppInfo>, quick: List<Tile>, favoritesHeader: String, quickHeader: String): List<LiteRow>
}
```
```kotlin
package com.cleanyourprojector.launcher.ui
class LiteHomeAdapter(onAppClick: (AppInfo) -> Unit, onAppLongClick: (AppInfo) -> Unit, onTileClick: (Tile) -> Unit)
    : RecyclerView.Adapter<LiteHomeAdapter.VH> {
    var upTarget: View?
    fun submit(rows: List<LiteRow>)     // DiffUtil keyed by header, as HomeRowsAdapter
    fun row(position: Int): LiteRow?
}
```
- Consumes: `HomeHost`, `HomeTab` (Task 3); `AppAdapter`, `TileAdapter`, `FocusRowRecyclerView`, `item_home_row.xml` (main).

- [ ] **Step 1: Write the failing tests**

```kotlin
class LiteRowsTest {
    private fun tile(title: String) = Tile(title, "", "", 0) {}

    @Test fun `quick tiles keep the fixed order and skip titles the box lacks`() {
        val got = LiteRows.quick(listOf(tile("Launcher"), tile("System settings"), tile("Wi-Fi"), tile("Focus")))
        assertEquals(listOf("Wi-Fi", "Focus", "Launcher"), got.map { it.title })
    }
    @Test fun `no favorites leaves only the quick settings shelf`() {
        val rows = LiteRows.build(emptyList(), listOf(tile("Wi-Fi")), "Favorites", "Quick settings")
        assertEquals(listOf("Quick settings"), rows.map { it.header })
        assertEquals(listOf("Wi-Fi"), rows[0].tiles.map { it.title })
        assertTrue(rows[0].apps.isEmpty())
    }
    @Test fun `quick settings shelf exists even with no quick tiles`() {
        assertEquals(1, LiteRows.build(emptyList(), emptyList(), "Favorites", "Quick settings").size)
    }
}
```
(The favorites-present case needs a `Drawable`, which the unit-test `android.jar` cannot construct; it is checked on the box in Task 6.)

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testLiteDebugUnitTest -q`
Expected: compile error, `LiteRows` unresolved.

- [ ] **Step 3: `LiteRows`** — `build` returns the Favorites row only when `favorites.isNotEmpty()`, then always the quick row.

- [ ] **Step 4: `LiteHomeAdapter`** — two view types (`TYPE_APPS = 0`, `TYPE_TILES = 1`), both inflate `item_home_row`; a holder keeps its inner adapter and `submit`s into it (`AppAdapter.submit` / `TileAdapter.submit`) when the type matches, else replaces it. App rows: `AppAdapter(row.apps, onAppClick, onAppLongClick, onFocus = {})`; tile rows: `TileAdapter(onClick = onTileClick, onFocus = {})` (the hero is hidden on Lite Home, so focus reports nothing). `upTarget` on position 0 only; `setHasFixedSize(true)`, `itemAnimator = null`, a recycled holder bound to a different header scrolls to 0 — mirror `HomeRowsAdapter` lines 54-82 without the pools and skeletons.

- [ ] **Step 5: `LiteHome` rows**
  - `onCreate()`: adapter with `host::launchApp`, `host::appMenu`, `{ it.action() }`; `upTarget = host.homeTabView`; set on `host.shelves`, `itemAnimator = null`.
  - `rows()` = `LiteRows.build(host.favoriteApps(), LiteRows.quick(host.settingsTiles()), getString(R.string.tab_favorites), getString(R.string.home_quick))`.
  - `show()`: `host.hideEmpty(); host.shelves.visibility = VISIBLE;` then the focus dance of `showShelves` (focus was inside and its card was recreated → first card; nothing focused or first show → first card), `firstShow = false`.
  - `scrollToTop()`: `host.shelves.scrollToPosition(0); focusFirstCard()`; `focusFirstCard()` posts, looks for `row_titles` child 0's `R.id.card`, retries up to 8 × 60 ms while `host.isHomeShowing`.
  - `onFavoritesChanged()`: `if (host.isHomeShowing) adapter.submit(rows())`.

- [ ] **Step 6: Run tests and builds**

Run: `./gradlew :app:testLiteDebugUnitTest :app:assembleLiteDebug -q`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add launcher
git commit -m "feat(launcher): lite Home shows Favorites and Quick settings shelves"
```

---

### Task 5: Lite Home clock

**Files:**
- Create: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/home/LiteClock.kt`
- Create: `launcher/app/src/main/res/layout/home_clock.xml`
- Modify: `launcher/app/src/main/res/layout/activity_launcher.xml` (a `ViewStub` after the hero)
- Modify: `launcher/app/src/lite/java/com/cleanyourprojector/launcher/home/LiteHome.kt`
- Test: `launcher/app/src/testLite/java/com/cleanyourprojector/launcher/home/LiteClockTest.kt`

**Interfaces:**
- Produces:
```kotlin
object LiteClock {
    data class Text(val time: String, val ampm: String, val date: String)
    /** [ampm] is empty in 24-hour mode. Formatters are cached per pattern. */
    fun format(now: Date, clock24h: Boolean, showSeconds: Boolean, locale: Locale = Locale.getDefault()): Text
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
class LiteClockTest {
    private fun at(h: Int, m: Int, s: Int) = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 23, h, m, s) }.time

    @Test fun `twelve hour clock with date`() {
        assertEquals(LiteClock.Text("9:26", "PM", "Friday, 23 October"), LiteClock.format(at(21, 26, 5), false, false, Locale.US))
    }
    @Test fun `twenty four hour clock with seconds`() {
        assertEquals(LiteClock.Text("21:26:05", "", "Friday, 23 October"), LiteClock.format(at(21, 26, 5), true, true, Locale.US))
    }
    @Test fun `twelve hour clock at midnight and noon`() {
        assertEquals("12:00" to "AM", LiteClock.format(at(0, 0, 0), false, false, Locale.US).let { it.time to it.ampm })
        assertEquals("12:00" to "PM", LiteClock.format(at(12, 0, 0), false, false, Locale.US).let { it.time to it.ampm })
    }
}
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testLiteDebugUnitTest -q` → `LiteClock` unresolved.

- [ ] **Step 3: `LiteClock`** — patterns `h:mm`/`H:mm` (+`:ss`), `a`, `EEEE, d MMMM`; three cached `SimpleDateFormat`s keyed by pattern+locale.

- [ ] **Step 4: Layout** — `home_clock.xml`: vertical `LinearLayout` id `home_clock`; a horizontal row (`gravity="bottom"`) of `home_clock_time` (96sp, `@font/outfit_light`, `text_primary`, `letterSpacing -0.02`, `includeFontPadding false`) and `home_clock_ampm` (28sp, `text_secondary`, `marginStart 10dp`, `marginBottom 14dp`); below, `home_clock_date` (20sp, `text_secondary`, `marginTop 2dp`). In `activity_launcher.xml`, right after the hero block: `<ViewStub android:id="@+id/home_clock_stub" android:inflatedId="@+id/home_clock" android:layout="@layout/home_clock" …/>` with the hero's constraints (`layout_width 0dp`, `marginStart 56dp`, `marginBottom 24dp`, bottom to `shelf_top`, start to parent, `width_percent 0.62`).

- [ ] **Step 5: `LiteHome` clock**
  - `onCreate()`: inflate the stub (`host.activity.findViewById<ViewStub>(R.id.home_clock_stub).inflate()`), keep the three `TextView`s, then `showClock(true)`; `host.setHeroVisible(false); host.setStatusClockVisible(false)` (Lite starts on Home).
  - `show()` begins with `showClock(true); host.setHeroVisible(false); host.setStatusClockVisible(false)`; `hide()` = `showClock(false); host.setHeroVisible(true); host.setStatusClockVisible(true)`.
  - `onTick(now)`: when the clock is visible, `LiteClock.format(now, host.prefs.clock24h, host.prefs.showSeconds)`; set each `TextView` only when its text changed; `ampm` GONE when empty.
  - `showClock(true)` also runs one `onTick(Date())` so the block never shows empty.

- [ ] **Step 6: Run tests and builds** — `./gradlew :app:testLiteDebugUnitTest :app:assembleLiteDebug :app:assembleFullDebug -q` → exit 0.

- [ ] **Step 7: Commit**

```bash
git add launcher
git commit -m "feat(launcher): large clock in the lite Home hero slot"
```

---

### Task 6: Ambient only in Full; README; on the box

**Files:**
- Modify: `launcher/app/src/main/java/com/cleanyourprojector/launcher/LauncherActivity.kt` (ambient sites)
- Modify: `README.md` (build/deploy section)

- [ ] **Step 1: Gate the ambient machinery** — `private val ambient: Boolean get() = Edition.AMBIENT && prefs.theme.dynamic`; replace every `prefs.theme.dynamic` / `t.dynamic` read in the activity with `ambient` (`applyTheme`, `updateClock`, `renderAppHero`, `renderTileHero`, `showEmpty`, `paintRoom`). `tintDrawable` becomes `BitmapDrawable?`; the three `Layers` bakes in `onCreate` run inside `if (Edition.AMBIENT)`; `animateTint` returns when `tintDrawable == null`.

- [ ] **Step 2: README** — build lines become `./gradlew :app:assembleFullDebug :app:assembleLiteDebug :app:testFullDebugUnitTest :app:testLiteDebugUnitTest`, APKs at `app/build/outputs/apk/{full,lite}/debug/app-{full,lite}-debug.apk`; a short "Lite edition" paragraph: no catalogue, no INTERNET, clock + Favorites + Quick settings on Home, same package so either replaces the other.

- [ ] **Step 3: Build, test, permissions** — `./gradlew :app:testFullDebugUnitTest :app:testLiteDebugUnitTest :app:assembleFullDebug :app:assembleLiteDebug -q` → exit 0; the Task 2 Step 8 `aapt` check → `0` then `1`.

- [ ] **Step 4: Lite on the box** — `export ANDROID_SERIAL=192.168.10.52:3333; adb install -r app/build/outputs/apk/lite/debug/app-lite-debug.apk; adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME; sleep 6; adb exec-out screencap -p > …/scratchpad/lite-home.png`. Look at it: big clock top-left, no hero text, no small clock top-right, Favorites then Quick settings shelves. Then `adb shell input keyevent DPAD_DOWN DPAD_DOWN DPAD_RIGHT DPAD_RIGHT; adb shell input keyevent 3` (HOME) and a second screencap: shelves back at the top, first card focused. Then `adb shell dumpsys package com.cleanyourprojector.launcher | grep -c INTERNET` → `0`. Switch to Apps (`DPAD_UP`, `DPAD_RIGHT`, screencap): hero and status clock back.

- [ ] **Step 5: Commit**

```bash
git add launcher README.md
git commit -m "feat(launcher): ambient layers only in the full edition; document the lite build"
```
