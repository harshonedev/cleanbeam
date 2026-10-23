package com.cleanyourprojector.remote.transport

/** Backoff for bringing the GATT server / advertiser up when the Bluetooth stack is not ready yet. */
object BleRetry {
    const val MAX_MS = 30_000L

    fun delayMs(attempt: Int): Long = minOf(1_000L shl attempt.coerceIn(0, 20), MAX_MS)
}
