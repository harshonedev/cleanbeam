package com.cleanyourprojector.launcher.apps

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val banner: Drawable?,      // leanback 16:9 banner when the app ships one
    val ambient: Bitmap?,       // tiny blurred render of the art, the room background of the Ambient theme
    val color: Int,             // dominant color from the icon/banner, for the fallback card
    val category: String?       // "Video", "Game", ... from ApplicationInfo.category
)
