package com.cleanyourprojector.remote.router

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** What the projector's pairing screen shows: who is connected and over which link. */
class ConnectionStatus {
    /** label → number of authenticated connections carrying it. */
    private val authenticated = ConcurrentHashMap<String, Int>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** "off", "advertising" or "unavailable". */
    @Volatile var bluetooth: String = "off"
        set(value) { field = value; notifyListeners() }

    fun onAuthenticated(label: String) { authenticated.merge(label, 1, Int::plus); notifyListeners() }
    fun onClosed(label: String) {
        val removed = authenticated.computeIfPresent(label) { _, n -> if (n <= 1) null else n - 1 } == null && !authenticated.containsKey(label)
        if (removed) notifyListeners()
    }

    fun clients(): List<String> = authenticated.keys.sorted()

    fun summary(): String {
        val c = clients()
        return if (c.isEmpty()) "No phone connected" else "Connected: " + c.joinToString(", ")
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() { listeners.forEach { runCatching { it() } } }
}
