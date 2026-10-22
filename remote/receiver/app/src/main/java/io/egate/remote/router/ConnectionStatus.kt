package io.egate.remote.router

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** What the projector's pairing screen shows: who is connected and over which link. */
class ConnectionStatus {
    private val authenticated = ConcurrentHashMap<String, Long>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** "off", "advertising" or "unavailable". */
    @Volatile var bluetooth: String = "off"
        set(value) { field = value; notifyListeners() }

    fun onAuthenticated(label: String) { authenticated[label] = System.currentTimeMillis(); notifyListeners() }
    fun onClosed(label: String) { if (authenticated.remove(label) != null) notifyListeners() }

    fun clients(): List<String> = authenticated.keys.sorted()

    fun summary(): String {
        val c = clients()
        return if (c.isEmpty()) "No phone connected" else "Connected: " + c.joinToString(", ")
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() { listeners.forEach { runCatching { it() } } }
}
