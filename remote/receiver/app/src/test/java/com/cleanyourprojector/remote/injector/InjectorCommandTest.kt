package io.egate.remote.injector

import io.egate.remote.protocol.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSink : InputInjector.Sink {
    val calls = mutableListOf<String>()
    var clock = 1000L
    override fun now(): Long = clock
    override fun displaySize(): Pair<Int, Int> = 1280 to 720
    override fun key(code: Int, down: Boolean, downTime: Long, eventTime: Long, metaState: Int): Boolean {
        calls += "key($code,${if (down) "down" else "up"},$downTime,$metaState)"; return true
    }
    override fun motion(action: Int, x: Float, y: Float, buttonState: Int, vScroll: Float): Boolean {
        calls += "motion($action,${x.toInt()},${y.toInt()},$buttonState,${vScroll.toInt()})"; return true
    }
    override fun exec(cmd: String) { calls += "exec($cmd)" }
    var canPaste = false
    override fun paste(text: String): Boolean { if (!canPaste) return false; calls += "paste($text)"; return true }
}

/** Maps only ASCII letters; everything else is "unmappable" like an emoji would be. */
private class AsciiMapper : InputInjector.CharMapper {
    override fun map(c: Char): List<InputInjector.KeyStroke>? =
        if (c in 'a'..'z') listOf(InputInjector.KeyStroke(29 + (c - 'a'), 0)) else null
}

class InjectorCommandTest {

    private fun make(): Pair<InputInjector, RecordingSink> {
        val sink = RecordingSink()
        return InputInjector(sink, AsciiMapper()) to sink
    }

    @Test
    fun `tap key sends down then up with same downTime`() {
        val (inj, sink) = make()
        inj.key(20, KeyAction.TAP)
        assertEquals(listOf("key(20,down,1000,0)", "key(20,up,1000,0)"), sink.calls)
    }

    @Test
    fun `down then up are separate injections`() {
        val (inj, sink) = make()
        inj.key(23, KeyAction.DOWN)
        sink.clock = 1500
        inj.key(23, KeyAction.UP)
        assertEquals(listOf("key(23,down,1000,0)", "key(23,up,1000,0)"), sink.calls)
    }

    @Test
    fun `text maps chars to key taps`() {
        val (inj, sink) = make()
        inj.text("ab")
        assertEquals(listOf("key(29,down,1000,0)", "key(29,up,1000,0)", "key(30,down,1000,0)", "key(30,up,1000,0)"), sink.calls)
    }

    @Test
    fun `text with unmappable chars uses input fallback`() {
        val (inj, sink) = make()
        inj.text("a😀b")
        assertEquals(
            listOf("key(29,down,1000,0)", "key(29,up,1000,0)", "exec(input text '😀')", "key(30,down,1000,0)", "key(30,up,1000,0)"),
            sink.calls
        )
    }

    @Test
    fun `unmappable runs are pasted when the sink can, between typed keys`() {
        val (inj, sink) = make()
        sink.canPaste = true
        inj.text("aनमस्ते😀b")
        assertEquals(
            listOf("key(29,down,1000,0)", "key(29,up,1000,0)", "paste(नमस्ते😀)", "key(30,down,1000,0)", "key(30,up,1000,0)"),
            sink.calls
        )
    }

    @Test
    fun `input fallback escapes single quotes`() {
        val (inj, sink) = make()
        inj.text("'")
        assertEquals(listOf("exec(input text ''\\''')"), sink.calls)
    }

    @Test
    fun `mouse move starts at centre and clamps to display`() {
        val (inj, sink) = make()
        inj.mouseMove(10, -5)
        inj.mouseMove(5000, 5000)
        assertEquals("motion(${InputInjector.ACTION_HOVER_MOVE},650,355,0,0)", sink.calls[0])
        assertEquals("motion(${InputInjector.ACTION_HOVER_MOVE},1279,719,0,0)", sink.calls[1])
    }

    @Test
    fun `click sends down press release up`() {
        val (inj, sink) = make()
        inj.click("left")
        val a = listOf(InputInjector.ACTION_DOWN, InputInjector.ACTION_BUTTON_PRESS, InputInjector.ACTION_BUTTON_RELEASE, InputInjector.ACTION_UP)
        assertEquals(a.map { "motion($it,640,360,${if (it == InputInjector.ACTION_DOWN || it == InputInjector.ACTION_BUTTON_PRESS) 1 else 0},0)" }, sink.calls)
    }

    @Test
    fun `scroll sends scroll motion with vscroll`() {
        val (inj, sink) = make()
        inj.scroll(3)
        assertEquals(listOf("motion(${InputInjector.ACTION_SCROLL},640,360,0,-3)"), sink.calls)
    }

    @Test
    fun `power actions map to keys or reboot`() {
        val (inj, sink) = make()
        inj.power("sleep"); inj.power("wake"); inj.power("toggle"); inj.power("reboot")
        assertTrue(sink.calls.contains("key(223,down,1000,0)"))
        assertTrue(sink.calls.contains("key(224,down,1000,0)"))
        assertTrue(sink.calls.contains("key(26,down,1000,0)"))
        assertEquals("exec(reboot)", sink.calls.last())
    }
}
