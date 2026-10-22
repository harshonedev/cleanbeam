package io.egate.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppRepository {

    // Packages we never want to show on the home grid.
    private val HIDDEN = setOf(
        "io.egate.launcher",
        "com.jixin.launcher5"
    )

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppInfo>()

        // Prefer Leanback entries, then fall back to standard launcher entries.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found)
        collect(pm, Intent.CATEGORY_LAUNCHER, found)

        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(
        pm: PackageManager,
        category: String,
        out: LinkedHashMap<String, AppInfo>
    ) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved = pm.queryIntentActivities(intent, 0)
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg in HIDDEN) continue
            if (out.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            val icon = ri.activityInfo.loadIcon(pm)
            out[pkg] = AppInfo(label, pkg, icon)
        }
    }

    fun launchIntentFor(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
    }
}
