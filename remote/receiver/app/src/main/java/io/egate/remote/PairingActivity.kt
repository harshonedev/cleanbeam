package io.egate.remote

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PairingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteService.start(this)
    }
}
