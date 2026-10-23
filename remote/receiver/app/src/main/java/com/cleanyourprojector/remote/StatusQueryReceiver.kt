package io.egate.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The launcher asks for the current connection state on resume; answer with a STATUS broadcast. */
class StatusQueryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUERY) return
        if (!RemoteService.running) RemoteService.startIfAllowed(context)
        RemoteService.broadcastStatus(context)
    }

    companion object { const val ACTION_QUERY = "io.egate.remote.QUERY_STATUS" }
}
