package io.egate.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.egate.remote.adb.AdbClient
import androidx.core.content.ContextCompat

class RemoteService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(TAG, "RemoteService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEBUG_SHELL -> {
                val cmd = intent.getStringExtra("cmd") ?: "id"
                Thread {
                    try {
                        AdbClient().use { it.connect(); Log.i(TAG, "debug shell '$cmd' → " + it.shell(cmd).trim()) }
                    } catch (e: Exception) {
                        Log.e(TAG, "debug shell failed", e)
                    }
                }.start()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "EGateRemote"
        const val ACTION_DEBUG_SHELL = "io.egate.remote.DEBUG_SHELL"
        private const val CHANNEL_ID = "egate_remote"
        private const val NOTIFICATION_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RemoteService::class.java))
        }
    }
}
