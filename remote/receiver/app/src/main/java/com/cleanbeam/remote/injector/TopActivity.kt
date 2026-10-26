package com.cleanbeam.remote.injector

import android.content.ComponentName

/**
 * Foreground package as seen by the helper process. The helper runs as shell (or root), which
 * holds MANAGE_ACTIVITY_TASKS, so it may ask ActivityTaskManager for the focused task. A normal
 * app gets nothing from UsageStats on some projector firmwares even with the appop granted.
 */
object TopActivity {
    fun current(): String? = try {
        val atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
        val info = atm.javaClass.getMethod("getFocusedRootTaskInfo").invoke(atm)
        (info?.javaClass?.getField("topActivity")?.get(info) as? ComponentName)?.packageName
    } catch (t: Throwable) {
        System.err.println("top activity unavailable: ${t.cause ?: t}")
        null
    }
}
