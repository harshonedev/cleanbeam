package io.egate.remote.injector

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

    override fun now(): Long = SystemClock.uptimeMillis()

    override fun displaySize(): Pair<Int, Int> = size

    override fun key(code: Int, down: Boolean, downTime: Long, eventTime: Long, metaState: Int): Boolean {
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val source = if (code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER) InputDevice.SOURCE_DPAD else InputDevice.SOURCE_KEYBOARD
        val ev = KeyEvent(downTime, eventTime, action, code, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_FROM_SYSTEM, source)
        return inject(ev)
    }

    override fun motion(action: Int, x: Float, y: Float, buttonState: Int, vScroll: Float): Boolean {
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

    private fun inject(ev: InputEvent): Boolean =
        try { injectMethod.invoke(inputManager, ev, INJECT_MODE_ASYNC) as Boolean } catch (t: Throwable) { false }

    companion object {
        private const val INJECT_MODE_ASYNC = 0
    }
}
