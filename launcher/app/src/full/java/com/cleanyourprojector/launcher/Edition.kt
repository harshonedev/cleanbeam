package com.cleanyourprojector.launcher

import com.cleanyourprojector.launcher.home.HomeHost
import com.cleanyourprojector.launcher.home.HomeTab
import com.cleanyourprojector.launcher.home.CatalogHome

/**
 * The one place a flavor differs in code. `const val`s are inlined by the compiler, so a branch
 * on them drops out of the other edition entirely.
 */
object Edition {
    const val NAME = "full"
    /** The Ambient theme: per-app art and colour painting the room. */
    const val AMBIENT = true

    fun home(host: HomeHost): HomeTab = CatalogHome(host)
}
