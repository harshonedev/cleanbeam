package com.cleanbeam.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.cleanbeam.remote.adb.AdbClient
import com.cleanbeam.remote.injector.InjectorClient
import com.cleanbeam.remote.router.AppsHandler
import com.cleanbeam.remote.router.CommandRouter
import com.cleanbeam.remote.router.ConnectionStatus
import com.cleanbeam.remote.transport.BleServer
import com.cleanbeam.remote.transport.WsServer
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
        running = true
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
        apps = AppsHandler(
            this,
            top = {
                try {
                    val r = injector.send("{\"t\":\"top\"}")
                    org.json.JSONObject(r).optString("pkg", "").takeIf { it.isNotEmpty() }
                } catch (e: Exception) { null }
            },
            start = { args ->
                try {
                    val r = injector.send(org.json.JSONObject().put("t", "start").put("args", args).toString())
                    if (r.contains("\"err\"")) Log.w(TAG, "helper start $args → $r")
                    !r.contains("\"err\"")
                } catch (e: Exception) { Log.w(TAG, "helper start failed", e); false }
            },
        )
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        router = CommandRouter(store, { json -> injector.send(json) }, apps, DeviceName.current(), version, status)
        ws = WsServer(this, router, apps).also { runCatching { it.start() }.onFailure { e -> Log.e(TAG, "ws start failed", e) } }
        ble = BleServer(this, router, status).also { runCatching { it.start() }.onFailure { e -> Log.e(TAG, "ble start failed", e) } }
        scheduler.scheduleWithFixedDelay({ pushStatusIfChanged() }, 2, 2, TimeUnit.SECONDS)
        status.addListener(statusListener)
        broadcastStatus(this)
        Log.i(TAG, "RemoteService created")
    }

    private val statusListener: () -> Unit = { broadcastStatus(this) }

    private fun pushStatusIfChanged() {
        try {
            val json = com.cleanbeam.remote.protocol.Codec.encodeEvent(router.newConnection(NOOP_SESSION).status())
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
        // Debug-only control surface: runs a shell command / injector message from an Intent extra.
        // The service is android:exported="false" (same-uid/adb only), but keep it out of release
        // builds entirely so no shipped build carries a shell backdoor.
        if (BuildConfig.DEBUG) when (intent?.action) {
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
        running = false
        status.removeListener(statusListener)
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
        const val TAG = "CleanBeamRemote"
        const val ACTION_DEBUG_SHELL = "com.cleanbeam.remote.DEBUG_SHELL"
        const val ACTION_DEBUG_INJECT = "com.cleanbeam.remote.DEBUG_INJECT"

        /** Live connection state for the pairing screen. */
        val status = ConnectionStatus()
        /** True while the service is alive; reported to the launcher so it can say "not running" honestly. */
        @Volatile var running = false
            private set
        private const val CHANNEL_ID = "projector_remote"
        private const val NOTIFICATION_ID = 1

        private val NOOP_SESSION = object : com.cleanbeam.remote.router.Session {
            override val label = "internal"
            override fun send(json: String) {}
            override fun close() {}
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RemoteService::class.java))
        }

        /**
         * Start only if the caller is allowed to launch a foreground service from where it is. Android 12+
         * refuses that from a broadcast another app sent us, and letting the exception escape crash-loops
         * the process until the system marks it bad and stops delivering our broadcasts at all.
         */
        fun startIfAllowed(ctx: Context): Boolean = try {
            start(ctx); true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot start foreground service from here: " + e.javaClass.simpleName)
            false
        }

        /** Launcher-facing status: who is connected and the Bluetooth state. Explicit to our launcher only. */
        const val ACTION_STATUS = "com.cleanbeam.remote.STATUS"
        const val LAUNCHER_PKG = "com.cleanbeam.launcher"
        /** Signature permission (declared in our manifest) gating both directions of the status IPC. */
        const val PERMISSION_STATUS = "com.cleanbeam.remote.permission.STATUS"

        fun broadcastStatus(ctx: Context) {
            ctx.sendBroadcast(Intent(ACTION_STATUS)
                .setPackage(LAUNCHER_PKG)
                .putExtra("alive", running)
                .putStringArrayListExtra("clients", ArrayList(status.clients()))
                .putExtra("bluetooth", status.bluetooth), PERMISSION_STATUS)
        }
    }
}
