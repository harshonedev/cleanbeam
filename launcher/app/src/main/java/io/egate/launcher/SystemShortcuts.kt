package io.egate.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Entries of the Settings quick menu that open system or vendor screens. Each entry lists
 * candidate intents, most specific first; the first one an installed activity handles wins,
 * so the menu adapts to whatever box the launcher runs on (EGate Atom 3X: jixin settings5).
 */
class SystemShortcut(val label: String, val group: String, val detail: String, private val candidates: List<Intent>) {
    fun resolve(context: Context): Intent? =
        candidates.firstOrNull { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object SystemShortcuts {

    private fun action(a: String) = Intent(a)
    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    val ALL = listOf(
        SystemShortcut("Wi-Fi", "Network", "", listOf(
            action(Settings.ACTION_WIFI_SETTINGS),
            action("jixin.intent.action.WIFI_ACTIVITY"))),
        SystemShortcut("Bluetooth", "Devices", "", listOf(
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
            component("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            action("jixin.intent.action.BLUETOOTH_MODE_SELECTER"))),
        SystemShortcut("Focus", "Projector", "Sharpen the picture", listOf(action("jixin.intent.action.FOCUS_SETTINGS"))),
        SystemShortcut("Keystone", "Projector", "Square up the picture and set the aspect ratio", listOf(action("jixin.intent.action.ASPECT_RATIO_CALE"))),
        SystemShortcut("Brightness", "Projector", "Pick a brightness mode", listOf(action("jixin.intent.action.BRIGHNESS_MODE"))),
        SystemShortcut("Projection", "Projector", "Front, rear, ceiling or table", listOf(action("jixin.intent.action.PROJECTION_TYPE"))),
        SystemShortcut("Input source", "Projector", "Switch to HDMI or another input", listOf(action("jixin.intent.action.INPUT_SOURCE"))),
        SystemShortcut("Projector settings", "Projector", "", listOf(action("jixin.intent.action.SETTINGS"))),
        SystemShortcut("System settings", "System", "", listOf(
            component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
            action(Settings.ACTION_SETTINGS)))
    )

    /** Entries this device can actually open, with the intent to fire. */
    fun available(context: Context): List<Pair<SystemShortcut, Intent>> =
        ALL.mapNotNull { s -> s.resolve(context)?.let { s to it } }
}
