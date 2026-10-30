package com.corpvpn.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectPlanTest {
    private fun key(id: String, host: String, protocol: Protocol) =
        VpnKey(id = id, name = host, protocol = protocol, endpointHost = host, endpointPort = 51820, config = "")

    @Test
    fun `tries target keys first, amneziawg before wireguard, then other answering servers`() {
        val a = Server("a", "a", listOf(key("a-wg", "a", Protocol.WIREGUARD), key("a-awg", "a", Protocol.AMNEZIAWG)))
        val b = Server("b", "b", listOf(key("b-wg", "b", Protocol.WIREGUARD)))
        val c = Server("c", "c", listOf(key("c-awg", "c", Protocol.AMNEZIAWG)))
        val d = Server("d", "d", listOf(key("d-wg", "d", Protocol.WIREGUARD)))
        val latencies = mapOf(
            "a" to Latency.Ms(90),
            "b" to Latency.Ms(150),
            "c" to Latency.Ms(40),
            "d" to Latency.Unreachable,
        )

        val plan = connectionCandidates(target = a, servers = listOf(a, b, c, d), latencies = latencies)

        assertEquals(listOf("a-awg", "a-wg", "c-awg", "b-wg"), plan.map { it.second.id })
    }

    @Test
    fun `manually chosen server is tried even if it did not answer ping`() {
        val a = Server("a", "a", listOf(key("a-wg", "a", Protocol.WIREGUARD)))
        val plan = connectionCandidates(a, listOf(a), mapOf("a" to Latency.Unreachable))
        assertEquals(listOf("a-wg"), plan.map { it.second.id })
    }
}
