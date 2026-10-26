package com.cleanbeam.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cleanbeam.launcher.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Prefs(context).keepAwake) KeepAliveService.start(context)
        }
    }
}
