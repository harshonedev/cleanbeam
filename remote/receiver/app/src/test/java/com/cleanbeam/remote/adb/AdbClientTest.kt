package com.cleanbeam.remote.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbClientTest {
    @Test
    fun `advertised tcp port is tried before the usual projector ports`() {
        assertEquals(listOf(5555, 3333), AdbClient.portsToTry(null))
        assertEquals(listOf(5555, 3333), AdbClient.portsToTry(5555))
        assertEquals(listOf(3333, 5555), AdbClient.portsToTry(3333))
        assertEquals(listOf(6666, 5555, 3333), AdbClient.portsToTry(6666))
        assertEquals(listOf(5555, 3333), AdbClient.portsToTry(0))
        assertEquals(listOf(5555, 3333), AdbClient.portsToTry(-1))
    }
}
