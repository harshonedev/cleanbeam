package io.egate.launcher

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val banner: Drawable?,      // leanback 16:9 banner when the app ships one
    val ambient: Bitmap?,       // tiny blurred render of the art, for the room background
    val color: Int,             // dominant color from the icon/banner, for ambient tint
    val category: String?,      // "Video", "Game", ... from ApplicationInfo.category
    val version: String?,
    val installTime: Long,
    val lastUsed: Long,         // 0 when unknown (no usage-stats access or never opened)
    val foregroundMs: Long      // total foreground time in the last 30 days, 0 when unknown
)
