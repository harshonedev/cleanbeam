package com.cleanbeam.launcher.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Finds a system settings activity by its label. Vendor projectors name the screen
 * "Focus" or "Keystone" (or the same words in Chinese) without sharing an intent action.
 */
object VendorScreens {
    private val OWN = setOf("com.cleanbeam.launcher", "com.cleanbeam.remote")
    private var index: List<ActivityLabel>? = null

    private class ActivityLabel(val label: String, val component: ComponentName)

    fun find(ctx: Context, hints: List<String>, flags: Int): Intent? {
        if (hints.isEmpty()) return null
        val hit = activities(ctx)
            .filter { matchesScreenLabel(it.label, hints) }
            .minByOrNull { it.label.length } ?: return null
        return Intent().setComponent(hit.component).addFlags(flags)
    }

    private fun activities(ctx: Context): List<ActivityLabel> {
        index?.let { return it }
        val pm = ctx.packageManager
        val out = ArrayList<ActivityLabel>()
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val installed = try { pm.getInstalledPackages(flags) } catch (_: Exception) { return emptyList() }
        for (pkg in installed) {
            if (pkg.packageName in OWN || !interesting(pkg.packageName)) continue
            val appFlags = pkg.applicationInfo?.flags ?: continue
            val system = appFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!system) continue
            for (ai in pkg.activities ?: continue) {
                if (!ai.exported || !ai.enabled) continue
                val label = ai.loadLabel(pm).toString().trim()
                if (label.isEmpty()) continue
                out += ActivityLabel(label, ComponentName(ai.packageName, ai.name))
            }
        }
        index = out
        return out
    }

    /** Skip the rest of the system image. Projector screens live in a settings-shaped package. */
    private fun interesting(packageName: String): Boolean {
        val n = packageName.lowercase()
        return listOf(
            "setting", "projector", "jixin", "droidlogic", "softwinner", "allwinner",
            "rockchip", "amlogic", "keystone", "xgimi", "fengmi", "wanbo",
        ).any { n.contains(it) }
    }
}

/** [hints] starting with `=` must equal the label; the rest match as substrings. */
internal fun matchesScreenLabel(label: String, hints: List<String>): Boolean {
    val hay = label.trim().lowercase()
    if (hay.isEmpty() || hints.isEmpty()) return false
    return hints.any { raw ->
        val hint = raw.lowercase()
        if (hint.startsWith("=")) hay == hint.removePrefix("=") else hay.contains(hint)
    }
}
