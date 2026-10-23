package io.egate.remote.transport

import java.util.concurrent.ConcurrentHashMap

/**
 * Connected BLE clients by address. Every way a client leaves the table ([add] over a stale entry,
 * [remove], [removeIf], [clear]) reports it through [onDrop], so its session is always closed and the
 * pairing screen never keeps counting a phone that is gone.
 */
class ClientTable<C : Any>(private val onDrop: (C) -> Unit) {
    private val map = ConcurrentHashMap<String, C>()

    operator fun get(key: String): C? = map[key]
    val values: Collection<C> get() = map.values

    fun add(key: String, client: C) { map.put(key, client)?.let(onDrop) }
    fun remove(key: String): C? = map.remove(key)?.also(onDrop)
    fun removeIf(key: String, client: C): Boolean = map.remove(key, client).also { if (it) onDrop(client) }
    fun clear() { val old = map.values.toList(); map.clear(); old.forEach(onDrop) }
}
