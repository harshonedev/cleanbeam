package io.egate.remote.router

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.egate.remote.protocol.ShortcutItem

/**
 * Projector and system screens the phone can jump to. Each entry lists candidate intents, most
 * specific first; the first one an installed activity handles wins, so the list adapts to the box
 * (EGate Atom 3X: vendor `com.jixin.settings5` actions, Android TV settings for Wi-Fi/Bluetooth).
 * Mirrors the launcher's Settings tiles; keep the two lists in step.
 */
class Shortcut(val id: String, val label: String, val group: String, private val candidates: List<Intent>) {
    fun resolve(ctx: Context): Intent? =
        candidates.firstOrNull { ctx.packageManager.queryIntentActivities(it, 0).isNotEmpty() }
            ?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    fun item() = ShortcutItem(id, label, group)
}

object Shortcuts {
    private fun action(a: String) = Intent(a)
    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    val ALL = listOf(
        Shortcut("input", "Input source", "Projector", listOf(action("jixin.intent.action.INPUT_SOURCE"))),
        Shortcut("wifi", "Wi-Fi", "Network", listOf(
            action(Settings.ACTION_WIFI_SETTINGS),
            action("jixin.intent.action.WIFI_ACTIVITY"))),
        Shortcut("bluetooth", "Bluetooth", "Devices", listOf(
            action(Settings.ACTION_BLUETOOTH_SETTINGS),
            component("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            action("jixin.intent.action.BLUETOOTH_MODE_SELECTER"))),
        Shortcut("focus", "Focus", "Projector", listOf(action("jixin.intent.action.FOCUS_SETTINGS"))),
        Shortcut("keystone", "Keystone", "Projector", listOf(action("jixin.intent.action.ASPECT_RATIO_CALE"))),
        Shortcut("brightness", "Brightness", "Projector", listOf(action("jixin.intent.action.BRIGHNESS_MODE"))),
        Shortcut("projection", "Projection", "Projector", listOf(action("jixin.intent.action.PROJECTION_TYPE"))),
        Shortcut("projector_settings", "Projector settings", "Projector", listOf(action("jixin.intent.action.SETTINGS"))),
        Shortcut("system_settings", "System settings", "System", listOf(
            component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
            action(Settings.ACTION_SETTINGS))),
    )

    /** Entries this box can open, in display order. */
    fun available(ctx: Context): List<Pair<Shortcut, Intent>> = ALL.mapNotNull { s -> s.resolve(ctx)?.let { s to it } }

    fun byId(id: String): Shortcut? = ALL.firstOrNull { it.id == id }
}
