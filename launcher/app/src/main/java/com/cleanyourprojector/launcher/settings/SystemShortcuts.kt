package com.cleanyourprojector.launcher.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.cleanyourprojector.launcher.R

/**
 * Entries of the Settings quick menu that open system or vendor screens. Each entry lists
 * candidate intents, most specific first; the first one an installed activity handles wins,
 * so the menu adapts to whatever box the launcher runs on (EGate Atom 3X: jixin settings5).
 */
class SystemShortcut(
    val label: String,
    val group: String,
    val detail: String,
    @DrawableRes val icon: Int,
    private val candidates: List<Intent>
) {
    fun resolve(context: Context): Intent? =
        candidates.firstOrNull { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object SystemShortcuts {

    private fun action(a: String) = Intent(a)
    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    val ALL = listOf(
        SystemShortcut("Wi-Fi", "Network", "", R.drawable.ic_wifi, listOf(
            action(Settings.ACTION_WIFI_SETTINGS),
            action("jixin.intent.action.WIFI_ACTIVITY"))),
        SystemShortcut("Bluetooth", "Devices", "", R.drawable.ic_bluetooth, listOf(
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
            component("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            action("jixin.intent.action.BLUETOOTH_MODE_SELECTER"))),
        SystemShortcut("Focus", "Projector", "Sharpen the picture", R.drawable.ic_focus, listOf(action("jixin.intent.action.FOCUS_SETTINGS"))),
        SystemShortcut("Keystone", "Projector", "Square up the picture and set the aspect ratio", R.drawable.ic_keystone, listOf(action("jixin.intent.action.ASPECT_RATIO_CALE"))),
        SystemShortcut("Brightness", "Projector", "Pick a brightness mode", R.drawable.ic_brightness, listOf(action("jixin.intent.action.BRIGHNESS_MODE"))),
        SystemShortcut("Projection", "Projector", "Front, rear, ceiling or table", R.drawable.ic_projection, listOf(action("jixin.intent.action.PROJECTION_TYPE"))),
        SystemShortcut("Input source", "Projector", "Switch to HDMI or another input", R.drawable.ic_input, listOf(action("jixin.intent.action.INPUT_SOURCE"))),
        SystemShortcut("Projector settings", "Projector", "", R.drawable.ic_projector, listOf(action("jixin.intent.action.SETTINGS"))),
        SystemShortcut("System settings", "System", "", R.drawable.ic_settings, listOf(
            component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
            action(Settings.ACTION_SETTINGS)))
    )

    /** Entries this device can actually open, with the intent to fire. */
    fun available(context: Context): List<Pair<SystemShortcut, Intent>> =
        ALL.mapNotNull { s -> s.resolve(context)?.let { s to it } }
}
