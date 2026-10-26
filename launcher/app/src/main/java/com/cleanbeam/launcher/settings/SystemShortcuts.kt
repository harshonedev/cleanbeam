package com.cleanbeam.launcher.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.cleanbeam.launcher.R

/**
 * Settings entries that open a system or vendor screen. Each entry tries known intent actions,
 * then a system activity whose label matches (so a projector that calls the screen "Focus" or
 * "对焦" still appears), then a generic Android settings screen where one exists.
 */
class SystemShortcut(
    val label: String,
    val group: String,
    val detail: String,
    @DrawableRes val icon: Int,
    private val candidates: List<Intent>,
    private val labelHints: List<String> = emptyList(),
    private val fallbacks: List<Intent> = emptyList(),
) {
    fun resolve(context: Context): Intent? =
        firstExplicit(context, candidates)
            ?: VendorScreens.find(context, labelHints, OPEN_FLAGS)
            ?: firstExplicit(context, fallbacks)

    private fun firstExplicit(context: Context, intents: List<Intent>): Intent? {
        for (candidate in intents) {
            val ai = context.packageManager.queryIntentActivities(candidate, 0).firstOrNull()?.activityInfo ?: continue
            return Intent().setComponent(ComponentName(ai.packageName, ai.name)).addFlags(OPEN_FLAGS)
        }
        return null
    }

    companion object {
        /** One fresh screen per tap. Vendor settings often share a task, and a plain NEW_TASK piles up. */
        const val OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
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
        SystemShortcut("Focus", "Projector", "Sharpen the picture", R.drawable.ic_focus,
            listOf(action("jixin.intent.action.FOCUS_SETTINGS")),
            labelHints = listOf("=focus", "autofocus", "auto focus", "对焦", "调焦")),
        SystemShortcut("Keystone", "Projector", "Square up the picture and set the aspect ratio", R.drawable.ic_keystone,
            listOf(action("jixin.intent.action.ASPECT_RATIO_CALE")),
            labelHints = listOf("keystone", "trapezoid", "corner correction", "梯形")),
        SystemShortcut("Brightness", "Projector", "Pick a brightness mode", R.drawable.ic_brightness,
            listOf(action("jixin.intent.action.BRIGHNESS_MODE")),
            labelHints = listOf("=brightness", "brightness mode", "亮度"),
            fallbacks = listOf(action(Settings.ACTION_DISPLAY_SETTINGS))),
        SystemShortcut("Projection", "Projector", "Front, rear, ceiling or table", R.drawable.ic_projection,
            listOf(action("jixin.intent.action.PROJECTION_TYPE")),
            labelHints = listOf("=projection", "projection mode", "projection type", "ceiling mount", "投影")),
        SystemShortcut("Input source", "Projector", "Switch to HDMI or another input", R.drawable.ic_input,
            listOf(
                action("jixin.intent.action.INPUT_SOURCE"),
                action("com.android.tv.action.VIEW_INPUTS")),
            labelHints = listOf("=input", "=inputs", "=source", "input source", "signal source", "hdmi", "信号源", "输入源")),
        SystemShortcut("Projector settings", "Projector", "Picture, sound and every projector option", R.drawable.ic_projector,
            listOf(action("jixin.intent.action.SETTINGS")),
            labelHints = listOf("=projector", "projector setting", "投影设置")),
        SystemShortcut("System settings", "System", "Date and time, language, storage, apps", R.drawable.ic_settings, listOf(
            component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
            action(Settings.ACTION_SETTINGS)))
    )

    /** Entries this device can actually open, with the intent to fire. */
    fun available(context: Context): List<Pair<SystemShortcut, Intent>> =
        ALL.mapNotNull { s -> s.resolve(context)?.let { s to it } }
}
