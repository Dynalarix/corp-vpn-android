package com.corpvpn.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectorTest {
    private fun key(id: String, host: String, protocol: Protocol = Protocol.AMNEZIAWG) =
        VpnKey(id = id, name = host, protocol = protocol, endpointHost = host, endpointPort = 51820, config = "")

    private val a = Server("a", "Server A", listOf(key("a1", "a"), key("a2", "a", Protocol.WIREGUARD)))
    private val b = Server("b", "Server B", listOf(key("b1", "b")))
    private val c = Server("c", "Server C", listOf(key("c1", "c")))
    private val servers = listOf(a, b, c)
    private val latencies = mapOf("a" to Latency.Ms(20), "b" to Latency.Ms(50), "c" to Latency.Ms(90))
    private val telegram = "https://api.telegram.org/"

    /** Fake tunnel: keys in [working] complete a handshake; records what happened. */
    private class FakeTunnel(val working: Set<String>, val throwing: Set<String> = emptySet()) : TunnelControl {
        var current: VpnKey? = null
        val log = mutableListOf<String>()
        override suspend fun up(key: VpnKey): Boolean {
            log += "up ${key.id}"
            if (key.id in throwing) throw IllegalStateException("bad config")
            current = key
            return key.id in working
        }
        override suspend fun down() {
            log += "down"
            current = null
        }
    }

    @Test
    fun `nearest connects to the lowest ping without checking sites`() = runTest {
        val tunnel = FakeTunnel(working = setOf("a1", "b1", "c1"))
        var checks = 0
        val connector = Connector(tunnel, { checks++; false })

        val result = connector.connect(ConnectMode.NEAREST, a, servers, latencies, listOf(telegram))

        assertEquals(ConnectResult.Connected(a, a.keys[0], verified = false), result)
        assertEquals(0, checks)
    }

    @Test
    fun `nearest falls back to the other key, then the next server`() = runTest {
        val tunnel = FakeTunnel(working = setOf("b1"), throwing = setOf("a2"))
        val result = Connector(tunnel, { true }).connect(ConnectMode.NEAREST, a, servers, latencies, emptyList())

        assertEquals(ConnectResult.Connected(b, b.keys[0], verified = false), result)
        assertEquals(listOf("up a1", "down", "up a2", "down", "up b1"), tunnel.log)
    }

    @Test
    fun `verified moves on when a check site does not open, and stays on the first server that passes`() = runTest {
        val tunnel = FakeTunnel(working = setOf("a1", "b1", "c1"))
        // Telegram is blocked through server A only.
        val connector = Connector(tunnel, { url -> !(url == telegram && tunnel.current?.endpointHost == "a") })
        val events = mutableListOf<ConnectEvent>()

        val result = connector.connect(ConnectMode.VERIFIED, a, servers, latencies, listOf(telegram)) { events += it }

        assertEquals(ConnectResult.Connected(b, b.keys[0], verified = true), result)
        assertTrue(ConnectEvent.ServerFailed(a, ServerProblem.SITES_FAILED, listOf(telegram)) in events)
    }

    @Test
    fun `verified waits for a site that needs a few seconds to answer`() = runTest {
        val tunnel = FakeTunnel(working = setOf("a1"))
        var attempts = 0
        val connector = Connector(tunnel, { attempts++ >= 3 })

        val result = connector.connect(ConnectMode.VERIFIED, a, servers, latencies, listOf(telegram))

        assertEquals(ConnectResult.Connected(a, a.keys[0], verified = true), result)
    }

    @Test
    fun `verified reports which sites no server could open`() = runTest {
        val tunnel = FakeTunnel(working = setOf("a1", "b1", "c1"))
        val connector = Connector(tunnel, { url -> url != telegram })

        val result = connector.connect(
            ConnectMode.VERIFIED, a, servers, latencies, listOf(telegram, "https://example.com/"),
        )

        val failed = assertIs<ConnectResult.Failed>(result)
        assertEquals("No server can open all check sites. Not working: api.telegram.org.", failed.message)
        assertEquals("down", tunnel.log.last())
    }

    @Test
    fun `reports the last error when no tunnel comes up`() = runTest {
        val tunnel = FakeTunnel(working = emptySet(), throwing = setOf("c1"))
        val result = Connector(tunnel, { true }).connect(ConnectMode.VERIFIED, a, servers, latencies, listOf(telegram))
        assertEquals(ConnectResult.Failed("Couldn't connect to any server. Last error: bad config"), result)
    }

    @Test
    fun `check list parsing`() {
        val text = """
            # Telegram
            api.telegram.org
            https://example.com/health   # full URL kept

            api.telegram.org
        """.trimIndent()
        assertEquals(listOf("https://api.telegram.org/", "https://example.com/health"), CheckList.parse(text))
        assertEquals("example.com", CheckList.displayName("https://example.com/health"))
    }
}
