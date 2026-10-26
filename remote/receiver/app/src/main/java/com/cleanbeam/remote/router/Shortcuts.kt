package com.cleanbeam.remote.router

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.cleanbeam.remote.protocol.ShortcutItem

/**
 * Projector and system screens the phone can jump to. Known intent actions first, then a system
 * activity whose label matches, then a generic Android settings screen. Mirrors the launcher's
 * Settings tiles; keep the two lists in step.
 */
class Shortcut(
    val id: String,
    val label: String,
    val group: String,
    private val candidates: List<Intent>,
    private val labelHints: List<String> = emptyList(),
    private val fallbacks: List<Intent> = emptyList(),
) {
    /**
     * The first candidate an installed activity handles, made explicit for that activity.
     * An implicit `am start -a` cannot start screens whose filter has no CATEGORY_DEFAULT, and
     * vendor settings screens often share one task, so a plain NEW_TASK start piles a new screen
     * onto it. CLEAR_TASK gives one fresh screen per tap.
     */
    fun resolve(ctx: Context): Intent? =
        firstExplicit(ctx, candidates)
            ?: VendorScreens.find(ctx, labelHints, OPEN_FLAGS)
            ?: firstExplicit(ctx, fallbacks)

    private fun firstExplicit(ctx: Context, intents: List<Intent>): Intent? {
        for (candidate in intents) {
            val ai = ctx.packageManager.queryIntentActivities(candidate, 0).firstOrNull()?.activityInfo ?: continue
            return Intent().setComponent(ComponentName(ai.packageName, ai.name)).addFlags(OPEN_FLAGS)
        }
        return null
    }

    companion object {
        const val OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    fun item() = ShortcutItem(id, label, group)
}

object Shortcuts {
    private fun action(a: String) = Intent(a)
    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    val ALL = listOf(
        Shortcut("input", "Input source", "Projector", listOf(
            action("jixin.intent.action.INPUT_SOURCE"),
            action("com.android.tv.action.VIEW_INPUTS")),
            labelHints = listOf("=input", "=inputs", "=source", "input source", "signal source", "hdmi", "信号源", "输入源")),
        Shortcut("wifi", "Wi-Fi", "Network", listOf(
            action(Settings.ACTION_WIFI_SETTINGS),
            action("jixin.intent.action.WIFI_ACTIVITY"))),
        Shortcut("bluetooth", "Bluetooth", "Devices", listOf(
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
            component("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            action("jixin.intent.action.BLUETOOTH_MODE_SELECTER"))),
        Shortcut("focus", "Focus", "Projector", listOf(action("jixin.intent.action.FOCUS_SETTINGS")),
            labelHints = listOf("=focus", "autofocus", "auto focus", "对焦", "调焦")),
        Shortcut("keystone", "Keystone", "Projector", listOf(action("jixin.intent.action.ASPECT_RATIO_CALE")),
            labelHints = listOf("keystone", "trapezoid", "corner correction", "梯形")),
        Shortcut("brightness", "Brightness", "Projector", listOf(action("jixin.intent.action.BRIGHNESS_MODE")),
            labelHints = listOf("=brightness", "brightness mode", "亮度"),
            fallbacks = listOf(action(Settings.ACTION_DISPLAY_SETTINGS))),
        Shortcut("projection", "Projection", "Projector", listOf(action("jixin.intent.action.PROJECTION_TYPE")),
            labelHints = listOf("=projection", "projection mode", "projection type", "ceiling mount", "投影")),
        Shortcut("projector_settings", "Projector settings", "Projector", listOf(action("jixin.intent.action.SETTINGS")),
            labelHints = listOf("=projector", "projector setting", "投影设置")),
        Shortcut("system_settings", "System settings", "System", listOf(
            component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
            action(Settings.ACTION_SETTINGS))),
    )

    /** Entries this box can open, in display order. */
    fun available(ctx: Context): List<Pair<Shortcut, Intent>> = ALL.mapNotNull { s -> s.resolve(ctx)?.let { s to it } }

    fun byId(id: String): Shortcut? = ALL.firstOrNull { it.id == id }
}
