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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteHomeTest {

    /** Construction must not touch any of this; the test only reads the tab's flags. */
    private val host = object : HomeHost {
        override val activity: Activity get() = error("unused")
        override val prefs: Prefs get() = error("unused")
        override val shelves: ShelvesRecyclerView get() = error("unused")
        override val homeTabView: View get() = error("unused")
        override val panel: SidePanel get() = error("unused")
        override val heroItem: Any? get() = null
        override val isHomeShowing: Boolean get() = true
        override fun apps(): List<AppInfo> = emptyList()
        override fun favoriteApps(): List<AppInfo> = emptyList()
        override fun settingsTiles(): List<Tile> = emptyList()
        override fun launchApp(app: AppInfo) {}
        override fun appMenu(app: AppInfo) {}
        override fun onCardFocused(item: Any) {}
        override fun setHero(title: String, subtitle: String, body: String?) {}
        override fun paintRoom(art: Bitmap?, color: Int) {}
        override fun showEmpty(title: Int, hint: Int) {}
        override fun hideEmpty() {}
        override fun showHome() {}
        override fun toast(message: String) {}
        override fun safeStart(intent: Intent) {}
    }

    @Test fun `lite has no Favorites tab, the favorites live on Home`() {
        assertFalse(LiteHome(host).favoritesTab)
    }

    @Test fun `lite starts on Home`() {
        assertTrue(LiteHome(host).startsOnHome)
    }
}
