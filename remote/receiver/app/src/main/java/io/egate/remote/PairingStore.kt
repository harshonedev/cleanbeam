package io.egate.remote

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Holds the long-lived pairing token and the short-lived on-screen pairing PIN. */
class PairingStore(private val backing: Backing) : io.egate.remote.router.Credentials {

    interface Backing {
        fun read(): String?
        fun write(token: String)
    }

    constructor(ctx: Context) : this(PrefsBacking(ctx))

    private val random = SecureRandom()

    override val token: String
        get() = backing.read() ?: regenerate()

    @Volatile
    override var pairingPin: String? = null

    fun regenerate(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val t = Base64.getEncoder().withoutPadding().encodeToString(bytes)
        backing.write(t)
        return t
    }

    private var pinFailures = 0

    fun newPin(): String {
        val pin = "%06d".format(random.nextInt(1_000_000))
        pinFailures = 0
        pairingPin = pin
        return pin
    }

    @Synchronized
    override fun onPairFailed() {
        if (++pinFailures >= MAX_PIN_FAILURES) pairingPin = null
    }

    override fun onPaired() {
        pairingPin = null
    }

    override fun matches(candidate: String): Boolean {
        val a = sha256(candidate)
        val b = sha256(token)
        return MessageDigest.isEqual(a, b)
    }

    companion object {
        const val MAX_PIN_FAILURES = 5
    }

    private fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

    private class PrefsBacking(ctx: Context) : Backing {
        private val prefs = ctx.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        override fun read(): String? = prefs.getString("token", null)
        override fun write(token: String) { prefs.edit().putString("token", token).commit() }
    }
}
