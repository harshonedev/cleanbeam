package com.cleanbeam.remote.injector

/**
 * A few projector families open their own power menu from a vendor keycode instead of the
 * Android power key. Jixin (and the many boxes that ship its settings app) is the common one.
 */
object VendorFirmware {
    private val JIXIN_SETTINGS = listOf("com.jixin.settings5", "com.jixin.settings")

    fun hasJixinSettings(): Boolean = JIXIN_SETTINGS.any { installed(it) }

    private fun installed(pkg: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("pm", "path", pkg))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("package:")
    } catch (_: Exception) {
        false
    }
}
