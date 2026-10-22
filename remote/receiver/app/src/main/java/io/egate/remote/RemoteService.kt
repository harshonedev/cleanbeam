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
import io.egate.remote.router.AppsHandler
import io.egate.remote.router.CommandRouter
import io.egate.remote.transport.BleServer
import io.egate.remote.transport.WsServer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RemoteService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    lateinit var injector: InjectorClient
        private set
    private lateinit var apps: AppsHandler
    private lateinit var router: CommandRouter
    private var ws: WsServer? = null
    private var ble: BleServer? = null
    private var lastStatus: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        injector = InjectorClient.create(applicationInfo.sourceDir, applicationInfo.uid)
        executor.execute {
            try {
                Log.i(TAG, "injector hello → " + injector.send("{\"t\":\"hello\"}"))
            } catch (e: Exception) {
                Log.e(TAG, "injector bootstrap failed", e)
            }
        }
        val store = PairingStore.get(this)
        apps = AppsHandler(this)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        router = CommandRouter(store, { json -> injector.send(json) }, apps, WsServer.SERVICE_NAME, version)
        ws = WsServer(this, router, apps).also { runCatching { it.start() }.onFailure { e -> Log.e(TAG, "ws start failed", e) } }
        ble = BleServer(this, router).also { runCatching { it.start() }.onFailure { e -> Log.e(TAG, "ble start failed", e) } }
        scheduler.scheduleWithFixedDelay({ pushStatusIfChanged() }, 2, 2, TimeUnit.SECONDS)
        Log.i(TAG, "RemoteService created")
    }

    private fun pushStatusIfChanged() {
        try {
            val json = io.egate.remote.protocol.Codec.encodeEvent(router.newConnection(NOOP_SESSION).status())
            if (json != lastStatus) {
                lastStatus = json
                ws?.broadcast(json)
                ble?.broadcast(json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "status push failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEBUG_SHELL -> {
                val cmd = intent.getStringExtra("cmd") ?: "id"
                executor.execute {
                    try {
                        AdbClient.connectAny().use { Log.i(TAG, "debug shell '$cmd' → " + it.shell(cmd).trim()) }
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
        scheduler.shutdownNow()
        ws?.stop(); ble?.stop()
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

        private val NOOP_SESSION = object : io.egate.remote.router.Session {
            override fun send(json: String) {}
            override fun close() {}
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RemoteService::class.java))
        }
    }
}
