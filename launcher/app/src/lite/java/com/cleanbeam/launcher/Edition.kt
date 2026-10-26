package com.cleanbeam.launcher

import com.cleanbeam.launcher.home.HomeHost
import com.cleanbeam.launcher.home.HomeTab
import com.cleanbeam.launcher.home.LiteHome

/**
 * The one place a flavor differs in code. `const val`s are inlined by the compiler, so a branch
 * on them drops out of the other edition entirely.
 */
object Edition {
    const val NAME = "lite"
    /** No Ambient theme: Lite shows nothing that moves with the focused app. */
    const val AMBIENT = false

    fun home(host: HomeHost): HomeTab = LiteHome(host)
}
