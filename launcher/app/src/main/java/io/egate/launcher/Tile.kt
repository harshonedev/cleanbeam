package io.egate.launcher

/** One quick action on the Settings tab. [detail] is the longer line shown in the hero when focused. */
class Tile(val title: String, val subtitle: String, val detail: String, val action: () -> Unit)
