package com.cleanyourprojector.remote

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

/** Full-screen pairing page: QR with the long token, plus a short-lived 6-digit PIN. */
class PairingActivity : AppCompatActivity() {

    private lateinit var store: PairingStore
    private lateinit var qr: ImageView
    private lateinit var pin: TextView
    private lateinit var ip: TextView
    private lateinit var status: TextView
    private val statusListener: () -> Unit = { runOnUiThread { renderStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
        RemoteService.start(this)
        store = PairingStore.get(this)
        qr = findViewById(R.id.qr)
        pin = findViewById(R.id.pin)
        ip = findViewById(R.id.ip)
        status = findViewById(R.id.status)
        findViewById<Button>(R.id.repair).setOnClickListener {
            store.regenerate()
            render()
        }
        findViewById<Button>(R.id.repair).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        store.newPin()
        render()
        RemoteService.status.addListener(statusListener)
        renderStatus()
    }

    override fun onPause() {
        RemoteService.status.removeListener(statusListener)
        store.clearPin()
        super.onPause()
    }

    private fun renderStatus() {
        val st = RemoteService.status
        val bt = when (st.bluetooth) {
            "advertising" -> "Bluetooth ready"
            "unavailable" -> "Bluetooth unavailable"
            else -> "Bluetooth off"
        }
        status.text = st.summary() + "  ·  " + bt
    }

    private fun render() {
        val address = Net.ipv4(this)
        val payload = JSONObject()
            .put("v", 1)
            .put("name", com.cleanyourprojector.remote.transport.WsServer.SERVICE_NAME)
            .put("ip", address ?: "")
            .put("port", 7272)
            .put("token", store.token)
            .toString()
        qr.setImageBitmap(encodeQr(payload, 420))
        pin.text = store.pairingPin?.let { it.substring(0, 3) + " " + it.substring(3) } ?: ""
        ip.text = address ?: "no Wi-Fi"
    }

    private fun encodeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}
