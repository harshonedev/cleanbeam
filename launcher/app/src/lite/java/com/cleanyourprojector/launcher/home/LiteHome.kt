package com.cleanyourprojector.launcher.home

import com.cleanyourprojector.launcher.R

/** Lite's Home tab. Filled in by the next tasks; for now it only shows the empty state. */
class LiteHome(host: HomeHost) : HomeTab(host) {
    override val startsOnHome: Boolean = true
    override val loadingText: Int = R.string.loading
    override fun show() { host.showEmpty(R.string.empty_favorites, R.string.empty_favorites_hint) }
    override fun scrollToTop() {}
    override fun onFavoritesChanged() {}
}
