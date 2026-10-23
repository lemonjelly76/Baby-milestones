package io.github.lemonjelly76.babymilestones.app

import io.github.lemonjelly76.babymilestones.app.data.ServerAddress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerAddressTest {

    private fun valid(input: String) = assertIs<ServerAddress.Valid>(ServerAddress.check(input), input)

    private fun invalid(input: String) = assertIs<ServerAddress.Invalid>(ServerAddress.check(input), input)

    @Test
    fun `https is fine anywhere`() {
        val address = valid(" https://pc.tailnet.ts.net:8445/ ")
        assertEquals("https://pc.tailnet.ts.net:8445", address.url)
        assertFalse(address.plainHttp)
    }

    @Test
    fun `plain http is allowed to addresses on the home network`() {
        listOf(
            "http://192.168.1.20:8083", "http://10.0.0.5:8083", "http://172.16.4.2:8083", "http://172.31.255.1",
            "http://nas.local:8083", "http://nas:8083", "http://homeserver.lan:8083", "http://box.home.arpa",
            "http://100.101.102.103:8083", "http://[fd12:3456::1]:8083", "http://localhost:8083",
        ).forEach { assertTrue(valid(it).plainHttp, it) }
    }

    @Test
    fun `plain http to anywhere else is refused`() {
        listOf("http://example.com", "http://8.8.8.8:8083", "http://172.32.0.1", "http://100.128.0.1", "http://[2001:db8::1]")
            .forEach { invalid(it) }
    }

    @Test
    fun `an address without a scheme gets http on the home network and https elsewhere`() {
        assertEquals("http://192.168.1.20:8083", valid("192.168.1.20:8083").url)
        assertEquals("https://pc.tailnet.ts.net:8445", valid("pc.tailnet.ts.net:8445").url)
    }

    @Test
    fun `nonsense is refused`() {
        listOf("", "   ", "ftp://192.168.1.20", "http://", "https://bad host").forEach { invalid(it) }
    }
}
