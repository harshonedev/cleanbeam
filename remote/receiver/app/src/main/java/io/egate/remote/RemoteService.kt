package io.egate.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.egate.remote.adb.AdbClient
import io.egate.remote.injector.InjectorClient
import java.util.concurrent.Executors

class RemoteService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    lateinit var injector: InjectorClient
        private set

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        injector = InjectorClient.create(applicationInfo.sourceDir)
        executor.execute {
            try {
                Log.i(TAG, "injector hello → " + injector.send("{\"t\":\"hello\"}"))
            } catch (e: Exception) {
                Log.e(TAG, "injector bootstrap failed", e)
            }
        }
        Log.i(TAG, "RemoteService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEBUG_SHELL -> {
                val cmd = intent.getStringExtra("cmd") ?: "id"
                executor.execute {
                    try {
                        AdbClient().use { it.connect(); Log.i(TAG, "debug shell '$cmd' → " + it.shell(cmd).trim()) }
                    } catch (e: Exception) {
                        Log.e(TAG, "debug shell failed", e)
                    }
                }
            }
            ACTION_DEBUG_INJECT -> {
                val json = intent.getStringExtra("json") ?: "{\"t\":\"hello\"}"
                executor.execute {
                    try {
                        Log.i(TAG, "debug inject $json → " + injector.send(json))
                    } catch (e: Exception) {
                        Log.e(TAG, "debug inject failed", e)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        injector.close()
        executor.shutdownNow()
        super.onDestroy()
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
        const val ACTION_DEBUG_INJECT = "io.egate.remote.DEBUG_INJECT"
        private const val CHANNEL_ID = "egate_remote"
        private const val NOTIFICATION_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RemoteService::class.java))
        }
    }
}
