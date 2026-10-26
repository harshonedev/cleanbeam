package com.cleanbeam.remote

import android.os.Build

/** Human name for this projector: what mDNS, the pairing QR, and the phone show. */
object DeviceName {
    fun current(): String {
        val model = Build.MODEL?.trim().orEmpty().takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val raw = when {
            model == null -> "Projector"
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        val cleaned = raw.replace(Regex("[^\\p{L}\\p{N} ._-]"), " ").replace(Regex("\\s+"), " ").trim()
        return cleaned.take(48).ifBlank { "Projector" }
    }
}
