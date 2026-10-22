package io.egate.remote.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import io.egate.remote.protocol.Chunker
import io.egate.remote.router.CommandRouter
import io.egate.remote.router.ConnectionStatus
import io.egate.remote.router.Session
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** BLE transport: GATT server with a write characteristic (cmd) and a notify characteristic (evt). */
class BleServer(private val ctx: Context, private val router: CommandRouter, private val status: ConnectionStatus? = null) {

    private val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gatt: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private lateinit var evtChar: BluetoothGattCharacteristic
    private val clients = ConcurrentHashMap<String, Client>()

    private inner class Client(val device: BluetoothDevice) {
        var mtu = 23
        val assembler = Chunker.Assembler()
        val outbox = LinkedBlockingQueue<ByteArray>()
        val sentAck = LinkedBlockingQueue<Boolean>(1)
        val connection: CommandRouter.Connection = router.newConnection(object : Session {
            override val label = "Bluetooth " + device.address
            override fun send(json: String) { enqueue(json.toByteArray()) }
            override fun close() { gatt?.cancelConnection(device) }
        })
        @Volatile var sending = false

        fun enqueue(bytes: ByteArray) {
            for (c in Chunker.split(bytes, mtu)) outbox.put(c)
            pump()
        }

        /** Sends queued chunks one at a time, waiting for onNotificationSent between them. */
        @Synchronized
        fun pump() {
            if (sending) return
            sending = true
            Thread {
                try {
                    var busyRetries = 0
                    while (clients[device.address] === this) {
                        val chunk = outbox.peek() ?: break
                        val g = gatt ?: break
                        sentAck.clear()
                        val status = g.notifyCharacteristicChanged(device, evtChar, false, chunk)
                        if (status == android.bluetooth.BluetoothStatusCodes.SUCCESS) {
                            outbox.poll()
                            busyRetries = 0
                            sentAck.poll(2, TimeUnit.SECONDS)
                        } else if (status == android.bluetooth.BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY && busyRetries++ < 20) {
                            Thread.sleep(20) // stack still busy with the previous notification; keep the chunk
                        } else {
                            Log.w(TAG, "notify failed status=$status; dropping ${outbox.size} chunks"); outbox.clear(); break
                        }
                    }
                } finally {
                    sending = false
                    if (outbox.isNotEmpty() && clients[device.address] === this) pump()
                }
            }.start()
        }
    }

    private var stateReceiver: BroadcastReceiver? = null

    /** Opens the GATT server now if Bluetooth is on, and (re)opens it whenever Bluetooth turns on later. */
    fun start() {
        if (stateReceiver == null) {
            stateReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> { Log.i(TAG, "bluetooth on → restarting gatt"); closeGatt(); openGatt() }
                        BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> closeGatt()
                    }
                }
            }.also { ctx.registerReceiver(it, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)) }
        }
        openGatt()
    }

    fun stop() {
        stateReceiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        stateReceiver = null
        closeGatt()
    }

    private fun openGatt() {
        val adapter = manager.adapter ?: run { Log.w(TAG, "no bluetooth adapter"); return }
        if (!adapter.isEnabled) { Log.w(TAG, "bluetooth disabled; will start when it turns on"); return }
        if (gatt != null) return
        gatt = manager.openGattServer(ctx, callback)?.also { it.addService(buildService()) }
        advertiser = adapter.bluetoothLeAdvertiser
        startAdvertising()
    }

    private fun closeGatt() {
        status?.bluetooth = "off"
        try { advertiser?.stopAdvertising(adCallback) } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        advertiser = null
        clients.clear()
    }

    fun broadcast(json: String) {
        clients.values.forEach { if (it.connection.authenticated) it.enqueue(json.toByteArray()) }
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val cmd = BluetoothGattCharacteristic(
            CMD_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        evtChar = BluetoothGattCharacteristic(
            EVT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        service.addCharacteristic(cmd)
        service.addCharacteristic(evtChar)
        return service
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        try {
            advertiser?.startAdvertising(settings, data, scanResponse, adCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "advertise needs BLUETOOTH_ADVERTISE", e)
        }
    }

    private val adCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(s: AdvertiseSettings) { Log.i(TAG, "ble advertising as $LOCAL_NAME"); status?.bluetooth = "advertising" }
        override fun onStartFailure(code: Int) {
            Log.w(TAG, "ble advertising failed: $code")
            status?.bluetooth = if (code == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) "advertising" else "unavailable"
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                clients[device.address] = Client(device)
                Log.i(TAG, "ble connected ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clients.remove(device.address)?.connection?.onClosed()
                Log.i(TAG, "ble disconnected ${device.address}")
                startAdvertising()
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            clients[device.address]?.mtu = mtu
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            if (responseNeeded) gatt?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (characteristic.uuid != CMD_UUID) return
            val client = clients[device.address] ?: return
            client.assembler.feed(value)?.let { client.connection.onMessage(String(it)) }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            if (responseNeeded) gatt?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            gatt?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            clients[device.address]?.sentAck?.offer(true)
        }
    }

    companion object {
        private const val TAG = "EGateRemote"
        const val LOCAL_NAME = "EGate Remote"
        val SERVICE_UUID: UUID = UUID.fromString("b6517522-6d7b-49d2-8488-664819daea01")
        val CMD_UUID: UUID = UUID.fromString("b6517522-6d7b-49d2-8488-664819daea02")
        val EVT_UUID: UUID = UUID.fromString("b6517522-6d7b-49d2-8488-664819daea03")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
