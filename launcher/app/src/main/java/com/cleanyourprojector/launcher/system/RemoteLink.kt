package com.cleanyourprojector.launcher.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.cleanyourprojector.launcher.apps.AppRepository

/**
 * Mirror of the EGate Remote service's connection state (the phone remote app, package
 * com.cleanyourprojector.remote). The service broadcasts [ACTION_STATUS] whenever a phone connects or
 * drops; we ask for a fresh copy with [ACTION_QUERY] each time the launcher resumes.
 */
class RemoteLink(private val context: Context, private val onChange: () -> Unit) {

    var clients: List<String> = emptyList(); private set
    var bluetooth: String = ""; private set
    /** True once the service has answered at least once this session. */
    var alive = false; private set

    val installed: Boolean get() = runCatching { context.packageManager.getPackageInfo(REMOTE_PKG, 0) }.isSuccess
    val connected: Boolean get() = clients.isNotEmpty()

    fun summary(): String = when {
        !installed -> "EGate Remote not installed"
        !alive -> "EGate Remote service not running"
        clients.isEmpty() -> "No phone connected"
        else -> "Connected: " + clients.joinToString(", ")
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != ACTION_STATUS) return
            clients = i.getStringArrayListExtra("clients") ?: emptyList()
            bluetooth = i.getStringExtra("bluetooth") ?: ""
            alive = i.getBooleanExtra("alive", true)
            onChange()
        }
    }

    fun start() {
        // Only a sender holding our signature-level permission can deliver STATUS, so nothing else
        // on the box can fake a connected phone.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_STATUS), PERMISSION_STATUS, null, ContextCompat.RECEIVER_EXPORTED)
        query()
    }

    fun stop() { runCatching { context.unregisterReceiver(receiver) } }

    fun query() {
        if (!installed) return
        context.sendBroadcast(Intent(ACTION_QUERY).setComponent(ComponentName(REMOTE_PKG, "$REMOTE_PKG.StatusQueryReceiver")))
    }

    /** The remote app's pairing screen, which also shows this state in full. */
    fun pairingIntent(): Intent? = AppRepository.launchIntentFor(context, REMOTE_PKG)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val REMOTE_PKG = "com.cleanyourprojector.remote"
        const val ACTION_STATUS = "com.cleanyourprojector.remote.STATUS"
        const val ACTION_QUERY = "com.cleanyourprojector.remote.QUERY_STATUS"
        const val PERMISSION_STATUS = "com.cleanyourprojector.remote.permission.STATUS"
    }
}
