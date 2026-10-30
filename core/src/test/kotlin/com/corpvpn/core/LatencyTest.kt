package com.corpvpn.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatencyTest {
    @Test
    fun `reads average from ping summary`() {
        val output = """
            PING 203.0.113.10 (203.0.113.10) 56(84) bytes of data.
            64 bytes from 203.0.113.10: icmp_seq=1 ttl=54 time=41.2 ms
            64 bytes from 203.0.113.10: icmp_seq=2 ttl=54 time=44.9 ms

            --- 203.0.113.10 ping statistics ---
            2 packets transmitted, 2 received, 0% packet loss, time 201ms
            rtt min/avg/max/mdev = 41.200/43.050/44.900/1.850 ms
        """.trimIndent()
        assertEquals(43L, PingOutput.averageMs(output))
    }

    @Test
    fun `falls back to per-reply times and handles sub-millisecond`() {
        assertEquals(30L, PingOutput.averageMs("time=20 ms\ntime=40 ms"))
        assertEquals(1L, PingOutput.averageMs("64 bytes from 10.0.0.1: icmp_seq=1 time<1 ms"))
    }

    @Test
    fun `no replies means unreachable`() {
        assertNull(PingOutput.averageMs("2 packets transmitted, 0 received, 100% packet loss, time 1001ms"))
        assertNull(PingOutput.averageMs(""))
    }

    private fun server(host: String) = Server(host = host, name = host, keys = emptyList())

    @Test
    fun `ranks by ping with measuring and unreachable last`() {
        val a = server("a"); val b = server("b"); val c = server("c"); val d = server("d")
        val latencies = mapOf(
            "a" to Latency.Unreachable,
            "b" to Latency.Ms(120),
            "c" to Latency.Measuring,
            "d" to Latency.Ms(35),
        )
        assertEquals(listOf(d, b, c, a), rankServers(listOf(a, b, c, d), latencies))
        assertEquals(d, bestServer(listOf(a, b, c, d), latencies))
    }

    @Test
    fun `no best server when nothing answered`() {
        val servers = listOf(server("a"), server("b"))
        assertNull(bestServer(servers, mapOf("a" to Latency.Unreachable, "b" to Latency.Measuring)))
    }
}
