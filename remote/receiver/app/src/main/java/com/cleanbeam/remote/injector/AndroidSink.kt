package com.cleanbeam.remote.injector

import android.content.ClipData
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method

/**
 * Real [InputInjector.Sink] for the helper process: injects via the hidden
 * InputManager.injectInputEvent (what scrcpy uses on Android 13).
 */
class AndroidSink : InputInjector.Sink, InputInjector.CharMapper {

    private val inputManager: Any
    private val injectMethod: Method
    private val charMap: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    @Volatile private var size: Pair<Int, Int> = 1280 to 720

    /** Real pointer via an evdev relative mouse when the kernel has one; injected MotionEvents otherwise (no sprite). */
    private val evdev: EvdevMouse? = EvdevMouse.open().also {
        System.err.println(if (it != null) "pointer: evdev ${it.deviceName}" else "pointer: injectInputEvent fallback")
    }

    init {
        val cls = Class.forName("android.hardware.input.InputManager")
        inputManager = cls.getMethod("getInstance").invoke(null)!!
        injectMethod = cls.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        refreshDisplaySize()
    }

    private fun refreshDisplaySize() {
        try {
            // DisplayInfo via DisplayManagerGlobal (hidden). Fall back to the default if anything throws.
            val dmg = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val inst = dmg.getMethod("getInstance").invoke(null)
            val info = dmg.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(inst, 0)
            val w = info.javaClass.getField("logicalWidth").getInt(info)
            val h = info.javaClass.getField("logicalHeight").getInt(info)
            if (w > 0 && h > 0) size = w to h
        } catch (_: Throwable) {
        }
    }

    private var lastX = size.first / 2f
    private var lastY = size.second / 2f

    override fun now(): Long = SystemClock.uptimeMillis()

    override fun displaySize(): Pair<Int, Int> = size

    override fun key(code: Int, down: Boolean, downTime: Long, eventTime: Long, metaState: Int): Boolean {
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val source = if (code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) InputDevice.SOURCE_DPAD else InputDevice.SOURCE_KEYBOARD
        val ev = KeyEvent(downTime, eventTime, action, code, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_FROM_SYSTEM, source)
        // Keys wait for the dispatcher like `input keyevent` does: HOME and other policy keys are
        // handled on the up event and need the down delivered first. Pointer events stay async.
        val ok = inject(ev, INJECT_MODE_WAIT_FOR_RESULT)
        if (!ok) System.err.println("inject key $code ${if (down) "down" else "up"} rejected")
        return ok
    }

    override fun motion(action: Int, x: Float, y: Float, buttonState: Int, vScroll: Float): Boolean {
        evdev?.let { m ->
            return try {
                when (action) {
                    MotionEvent.ACTION_HOVER_MOVE -> m.move((x - lastX).toInt(), (y - lastY).toInt()).let { lastX = x; lastY = y; true }
                    MotionEvent.ACTION_BUTTON_PRESS -> { m.click(buttonState == InputInjector.BUTTON_SECONDARY); true }
                    MotionEvent.ACTION_SCROLL -> { m.scroll((-vScroll).toInt()); true }
                    else -> true // DOWN/UP/BUTTON_RELEASE are implied by click()
                }
            } catch (e: Exception) {
                System.err.println("evdev write failed: $e"); false
            }
        }
        val now = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = if (buttonState != 0) 1f else 0f; size = 1f
            if (action == MotionEvent.ACTION_SCROLL) setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }
        val ev = MotionEvent.obtain(now, now, action, 1, arrayOf(props), arrayOf(coords), 0, buttonState,
            1f, 1f, -1, 0, InputDevice.SOURCE_MOUSE, 0)
        val ok = inject(ev)
        ev.recycle()
        return ok
    }

    /**
     * Clipboard-and-paste for text the key map cannot type. Uses the hidden IClipboard binder as
     * com.android.shell (this process is spawned by adbd, so it is uid shell or root; shell holds
     * READ_CLIPBOARD_IN_BACKGROUND, which is what lets a non-focused caller set the clip). The
     * focused field then receives KEYCODE_PASTE, which TextView turns into an insert.
     */
    private val clipboard: Clipboard? by lazy { Clipboard.open().also { System.err.println(if (it != null) "clipboard: ok" else "clipboard: unavailable") } }

    private val clipClearer = java.util.Timer("projector-clip-clear", true)
    private var clipClearTask: java.util.TimerTask? = null

    override fun paste(text: String): Boolean {
        val c = clipboard ?: return false
        clipClearTask?.cancel()
        if (!c.set(text)) return false
        val t = now()
        val ok = key(KeyEvent.KEYCODE_PASTE, true, t, t, 0) and key(KeyEvent.KEYCODE_PASTE, false, t, t, 0)
        // What was typed may be a password: do not leave it on the projector's clipboard for
        // other apps. The paste key is delivered synchronously but the app handles it on its own
        // thread, so give it a moment before clearing.
        clipClearTask = object : java.util.TimerTask() { override fun run() { c.clear() } }
            .also { clipClearer.schedule(it, CLIP_CLEAR_DELAY_MS) }
        return ok
    }

    private class Clipboard private constructor(private val service: Any, private val setMethod: Method, private val clearMethod: Method?) {
        fun set(text: String): Boolean = try {
            val clip = ClipData.newPlainText("projector-remote", text)
            // Keyboard apps and clipboard managers skip clips flagged sensitive (Android 13+).
            clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            val args: Array<Any?> = when (setMethod.parameterTypes.size) {
                2 -> arrayOf(clip, SHELL_PKG)                      // < 29
                3 -> arrayOf(clip, SHELL_PKG, 0)                   // 29-30: (clip, pkg, userId)
                4 -> arrayOf(clip, SHELL_PKG, null, 0)             // 31-33: (clip, pkg, attributionTag, userId)
                else -> arrayOf(clip, SHELL_PKG, null, 0, 0)       // 34+: + deviceId
            }
            setMethod.invoke(service, *args); true
        } catch (t: Throwable) {
            System.err.println("clipboard set failed: ${t.cause ?: t}"); false
        }

        /** clearPrimaryClip(pkg[, attributionTag], userId[, deviceId]); best effort. */
        fun clear() {
            val m = clearMethod ?: return
            try {
                val args: Array<Any?> = when (m.parameterTypes.size) {
                    1 -> arrayOf(SHELL_PKG)
                    2 -> arrayOf(SHELL_PKG, 0)
                    3 -> arrayOf(SHELL_PKG, null, 0)
                    else -> arrayOf(SHELL_PKG, null, 0, 0)
                }
                m.invoke(service, *args)
            } catch (t: Throwable) {
                System.err.println("clipboard clear failed: ${t.cause ?: t}")
            }
        }

        companion object {
            private const val SHELL_PKG = "com.android.shell"
            fun open(): Clipboard? = try {
                val sm = Class.forName("android.os.ServiceManager")
                val binder = sm.getMethod("getService", String::class.java).invoke(null, "clipboard") as IBinder
                val stub = Class.forName("android.content.IClipboard\$Stub")
                val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
                val m = service.javaClass.methods.filter { it.name == "setPrimaryClip" }.minByOrNull { it.parameterTypes.size }
                    ?: throw NoSuchMethodException("setPrimaryClip")
                val clear = service.javaClass.methods.filter { it.name == "clearPrimaryClip" }.minByOrNull { it.parameterTypes.size }
                Clipboard(service, m, clear)
            } catch (t: Throwable) {
                System.err.println("clipboard unavailable: $t"); null
            }
        }
    }

    override fun exec(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
        } catch (_: Exception) {
        }
    }

    override fun map(c: Char): List<InputInjector.KeyStroke>? {
        val events = charMap.getEvents(charArrayOf(c)) ?: return null
        // getEvents returns down/up pairs; keep one stroke per down event.
        return events.filter { it.action == KeyEvent.ACTION_DOWN }.map { InputInjector.KeyStroke(it.keyCode, it.metaState) }
    }

    private fun inject(ev: InputEvent, mode: Int = INJECT_MODE_ASYNC): Boolean =
        try { injectMethod.invoke(inputManager, ev, mode) as Boolean } catch (t: Throwable) { System.err.println("inject failed: ${t.cause ?: t}"); false }

    companion object {
        private const val INJECT_MODE_ASYNC = 0
        private const val INJECT_MODE_WAIT_FOR_RESULT = 1
        private const val CLIP_CLEAR_DELAY_MS = 1_500L
    }
}
