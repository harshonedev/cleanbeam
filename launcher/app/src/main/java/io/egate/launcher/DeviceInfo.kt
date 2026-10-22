package io.egate.launcher

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

/** One-line live state for the Settings tiles. Every call is cheap and never throws. */
object DeviceInfo {

    fun wifi(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "Not connected"
        val caps = cm.getNetworkCapabilities(net) ?: return "Not connected"
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "Wired network" else "Not connected"
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = runCatching { wm.connectionInfo }.getOrNull()
        // The SSID is redacted to "<unknown ssid>" unless the app holds a location permission.
        val ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && !it.startsWith("<unknown") && it != "0x" }
        val ip = cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        val bars = info?.rssi?.takeIf { it != -127 }?.let { rssi ->
            val level = if (Build.VERSION.SDK_INT >= 30) wm.calculateSignalLevel(rssi) else @Suppress("DEPRECATION") WifiManager.calculateSignalLevel(rssi, 5)
            val max = if (Build.VERSION.SDK_INT >= 30) wm.maxSignalLevel else 4
            listOf("Weak", "Fair", "Good", "Strong", "Excellent")[(level * 4 / max.coerceAtLeast(1)).coerceIn(0, 4)] + " signal"
        }
        return listOfNotNull(ssid ?: "Connected", ip, bars).joinToString("  ·  ")
    }

    fun bluetooth(context: Context): String {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return "Not available"
        if (!adapter.isEnabled) return "Off"
        val bonded = runCatching { adapter.bondedDevices.toList() }.getOrNull() ?: return "On"
        if (bonded.isEmpty()) return "On  ·  nothing paired"
        val connected = bonded.filter { isConnected(it) }
        val name = { d: BluetoothDevice -> runCatching { d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: d.address }
        return if (connected.isNotEmpty()) "Connected to " + connected.joinToString(", ") { name(it) }
        else "On  ·  ${bonded.size} paired, none connected: " + bonded.take(3).joinToString(", ") { name(it) }
    }

    /** BluetoothDevice.isConnected is hidden; on failure assume not connected. */
    private fun isConnected(d: BluetoothDevice): Boolean =
        runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)

    fun system(): String = "Android ${Build.VERSION.RELEASE}  ·  ${Build.MANUFACTURER} ${Build.MODEL}"
    fun firmware(): String = "Firmware ${Build.DISPLAY}"
}
