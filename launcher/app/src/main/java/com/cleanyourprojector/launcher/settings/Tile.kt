package com.cleanyourprojector.launcher.settings

import androidx.annotation.DrawableRes

/**
 * One quick action on the Settings tab. [icon] is the glyph drawn in the tile's top corner and
 * [detail] is the longer line shown in the hero when focused.
 */
class Tile(
    val title: String,
    val subtitle: String,
    val detail: String,
    @DrawableRes val icon: Int,
    val action: () -> Unit
)
