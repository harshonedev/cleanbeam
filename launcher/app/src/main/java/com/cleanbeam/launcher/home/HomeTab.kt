package com.cleanbeam.launcher.home

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import com.cleanbeam.launcher.Prefs
import com.cleanbeam.launcher.apps.AppInfo
import com.cleanbeam.launcher.settings.Tile
import com.cleanbeam.launcher.ui.ShelvesRecyclerView
import com.cleanbeam.launcher.ui.SidePanel

/** What the activity lends the Home tab. The activity implements it; the edition's [HomeTab] uses it. */
interface HomeHost {
    val activity: Activity
    val prefs: Prefs
    /** The vertical list the Home tab fills; the activity shows and hides it with the tab. */
    val shelves: ShelvesRecyclerView
    /** D-pad up from the first shelf lands here (the Home tab label). */
    val homeTabView: View
    val panel: SidePanel
    /** Whatever the hero shows right now: an AppInfo, a Tile, or something the Home tab put there. */
    val heroItem: Any?
    val isHomeShowing: Boolean
    fun apps(): List<AppInfo>
    fun favoriteApps(): List<AppInfo>
    /** The Settings tab's tiles with their actions wired, for a Home tab that wants a few of them. */
    fun settingsTiles(): List<Tile>
    fun launchApp(app: AppInfo)
    fun appMenu(app: AppInfo)
    fun onCardFocused(item: Any)
    /** Animated hero swap; a blank [body] hides that line. */
    fun setHero(title: String, subtitle: String, body: String?)
    /** Ambient theme only: wash the room with [art] in [color]. A no-op in any other theme or edition. */
    fun paintRoom(art: Bitmap?, color: Int)
    fun showEmpty(title: Int, hint: Int)
    fun hideEmpty()
    /** Select the Home tab, or redraw it if it is already selected. */
    fun showHome()
    fun toast(message: String)
    fun safeStart(intent: Intent)
}

/**
 * The Home tab of one edition. Full fills it with the streaming catalogue; Lite with a clock,
 * the favorites and a few settings. The activity owns the views and the other tabs and calls in.
 */
abstract class HomeTab(protected val host: HomeHost) {
    /** Lite lands on Home at once; Full only when the catalogue is set up. */
    abstract val startsOnHome: Boolean
    /** Hero text while the first app load runs and Home is the start tab. */
    abstract val loadingText: Int
    /** Whether the launcher shows a Favorites tab beside Home; Lite keeps the favorites on Home only. */
    open val favoritesTab: Boolean = true
    /** After the activity's views exist. */
    open fun onCreate() {}
    /** Home is the selected tab: draw it. Also called after each app reload while Home shows. */
    abstract fun show()
    /** Another tab took over. */
    open fun hide() {}
    /** HOME key while already on Home: back to the top, first card focused. */
    abstract fun scrollToTop()
    /** A favorite was pinned, unpinned or moved. */
    abstract fun onFavoritesChanged()
    /** Render the hero for an item this tab put on screen; false when it is not ours. */
    open fun renderHero(item: Any): Boolean = false
    /** Extra rows for the Launcher settings panel. */
    open fun settingsRows(): List<SidePanel.Row> = emptyList()
    open fun onDestroy() {}
}
