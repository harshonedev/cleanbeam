package io.egate.remote.injector

import io.egate.remote.protocol.KeyAction

/**
 * Turns remote commands into input events. All Android calls go through [Sink] so the logic
 * is unit-testable on the JVM; [AndroidSink] is the real implementation.
 */
class InputInjector(private val sink: Sink, private val mapper: CharMapper) {

    interface Sink {
        fun now(): Long
        fun displaySize(): Pair<Int, Int>
        fun key(code: Int, down: Boolean, downTime: Long, eventTime: Long, metaState: Int): Boolean
        fun motion(action: Int, x: Float, y: Float, buttonState: Int, vScroll: Float): Boolean
        fun exec(cmd: String)
        /** Types [text] by putting it on the clipboard and pressing Paste; false when that path is unavailable. */
        fun paste(text: String): Boolean = false
    }

    data class KeyStroke(val code: Int, val metaState: Int)

    interface CharMapper {
        /** Key strokes that type [c], or null when the virtual keyboard cannot produce it. */
        fun map(c: Char): List<KeyStroke>?
    }

    private var x = -1f
    private var y = -1f
    private val downTimes = HashMap<Int, Long>()

    fun key(code: Int, action: KeyAction, metaState: Int = 0): Boolean = when (action) {
        KeyAction.TAP -> {
            val t = sink.now()
            sink.key(code, true, t, t, metaState) and sink.key(code, false, t, t, metaState)
        }
        KeyAction.DOWN -> {
            val t = sink.now()
            downTimes[code] = t
            sink.key(code, true, t, t, metaState)
        }
        KeyAction.UP -> {
            val t = downTimes.remove(code) ?: sink.now()
            sink.key(code, false, t, sink.now(), metaState)
        }
    }

    fun text(s: String): Boolean {
        var ok = true
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) {
                // Characters the virtual keyboard cannot produce (emoji, Devanagari, accents):
                // paste them; `input text` is the last resort and only handles ASCII itself.
                val run = pending.toString()
                if (!sink.paste(run)) sink.exec("input text " + shellQuote(run))
                pending.setLength(0)
            }
        }
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = Character.charCount(cp)
            val strokes = if (len == 1) mapper.map(s[i]) else null
            if (strokes == null) {
                pending.append(s, i, i + len)
            } else {
                flush()
                for (k in strokes) ok = key(k.code, KeyAction.TAP, k.metaState) && ok
            }
            i += len
        }
        flush()
        return ok
    }

    fun mouseMove(dx: Int, dy: Int): Boolean {
        ensureCursor()
        val (w, h) = sink.displaySize()
        x = (x + dx).coerceIn(0f, (w - 1).toFloat())
        y = (y + dy).coerceIn(0f, (h - 1).toFloat())
        return sink.motion(ACTION_HOVER_MOVE, x, y, 0, 0f)
    }

    fun click(button: String): Boolean {
        ensureCursor()
        val state = if (button == "right") BUTTON_SECONDARY else BUTTON_PRIMARY
        var ok = sink.motion(ACTION_DOWN, x, y, state, 0f)
        ok = sink.motion(ACTION_BUTTON_PRESS, x, y, state, 0f) && ok
        ok = sink.motion(ACTION_BUTTON_RELEASE, x, y, 0, 0f) && ok
        ok = sink.motion(ACTION_UP, x, y, 0, 0f) && ok
        return ok
    }

    fun scroll(dy: Int): Boolean {
        ensureCursor()
        // Android's AXIS_VSCROLL is positive when scrolling content up; our dy is positive = down.
        return sink.motion(ACTION_SCROLL, x, y, 0, -dy.toFloat())
    }

    fun power(action: String): Boolean = when (action) {
        "sleep" -> key(KEYCODE_SLEEP, KeyAction.TAP)
        "wake" -> key(KEYCODE_WAKEUP, KeyAction.TAP)
        "reboot" -> { sink.exec("reboot"); true }
        else -> key(KEYCODE_POWER, KeyAction.TAP)
    }

    private fun ensureCursor() {
        if (x < 0 || y < 0) {
            val (w, h) = sink.displaySize()
            x = w / 2f
            y = h / 2f
        }
    }

    companion object {
        // android.view.MotionEvent constants, duplicated so the JVM tests need no Android stubs.
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_HOVER_MOVE = 7
        const val ACTION_SCROLL = 8
        const val ACTION_BUTTON_PRESS = 11
        const val ACTION_BUTTON_RELEASE = 12
        const val BUTTON_PRIMARY = 1
        const val BUTTON_SECONDARY = 2

        const val KEYCODE_POWER = 26
        const val KEYCODE_SLEEP = 223
        const val KEYCODE_WAKEUP = 224

        fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    }
}
