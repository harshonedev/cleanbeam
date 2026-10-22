package io.egate.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val color: Int   // dominant color from the icon, for per-app focus glow
)
