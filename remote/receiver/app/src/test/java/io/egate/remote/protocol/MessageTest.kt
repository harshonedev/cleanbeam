package io.egate.remote.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun `decodes key tap`() {
        val cmd = Codec.decodeCommand("""{"t":"key","code":20,"action":"tap"}""")
        assertEquals(Command.Key(20, KeyAction.TAP), cmd)
    }

    @Test
    fun `decodes auth`() {
        val cmd = Codec.decodeCommand("""{"t":"auth","token":"Z3VpZC10b2tlbi1iYXNlNjQ="}""")
        assertEquals(Command.Auth("Z3VpZC10b2tlbi1iYXNlNjQ="), cmd)
    }

    @Test
    fun `decodes pair`() {
        val cmd = Codec.decodeCommand("""{"t":"pair","code":"482913"}""")
        assertEquals(Command.Pair("482913"), cmd)
    }

    @Test
    fun `decodes mouse and power`() {
        assertEquals(Command.Mouse(12, -4), Codec.decodeCommand("""{"t":"mouse","dx":12,"dy":-4}"""))
        assertEquals(Command.Power("sleep"), Codec.decodeCommand("""{"t":"power","action":"sleep"}"""))
    }

    @Test
    fun `unknown type becomes Unknown`() {
        assertEquals(Command.Unknown("foo"), Codec.decodeCommand("""{"t":"foo"}"""))
    }

    @Test
    fun `malformed json becomes Unknown with empty type`() {
        assertEquals(Command.Unknown(""), Codec.decodeCommand("not json"))
    }

    @Test
    fun `encodes status`() {
        val json = Codec.encodeEvent(Event.Status("io.egate.launcher", true, 7, 15))
        assertEquals("""{"t":"status","app":"io.egate.launcher","screenOn":true,"volume":7,"maxVolume":15}""", json)
    }

    @Test
    fun `encodes status with null app`() {
        val json = Codec.encodeEvent(Event.Status(null, false, 0, 15))
        assertEquals("""{"t":"status","app":null,"screenOn":false,"volume":0,"maxVolume":15}""", json)
    }

    @Test
    fun `encodes apps list`() {
        val json = Codec.encodeEvent(Event.Apps(listOf(AppItem("com.google.android.youtube.tv", "YouTube"))))
        assertEquals("""{"t":"apps","items":[{"pkg":"com.google.android.youtube.tv","label":"YouTube"}]}""", json)
    }

    @Test
    fun `encodes auth_ok, pair_ok, pong and err`() {
        assertEquals("""{"t":"auth_ok","name":"EGate Atom3X","version":"1.0"}""", Codec.encodeEvent(Event.AuthOk("EGate Atom3X", "1.0")))
        assertEquals("""{"t":"pair_ok","token":"abc","name":"EGate Atom3X"}""", Codec.encodeEvent(Event.PairOk("abc", "EGate Atom3X")))
        assertEquals("""{"t":"pong"}""", Codec.encodeEvent(Event.Pong))
        assertEquals("""{"t":"auth_fail"}""", Codec.encodeEvent(Event.AuthFail))
        assertTrue(Codec.encodeEvent(Event.Err("unknown type: foo")).contains("\"msg\":\"unknown type: foo\""))
    }
}
