package com.corpvpn.core

/**
 * The keys to try, in order, when the user taps Connect: every key of the chosen
 * server first, then the keys of the other servers that answered ping (best first).
 * Within a server, AmneziaWG keys come before plain WireGuard ones because
 * AmneziaWG gets through networks that block WireGuard.
 */
fun connectionCandidates(
    target: Server,
    servers: List<Server>,
    latencies: Map<String, Latency>,
): List<Pair<Server, VpnKey>> {
    val fallbacks = rankServers(servers, latencies)
        .filter { it.host != target.host && latencies[it.host] is Latency.Ms }
    return (listOf(target) + fallbacks).flatMap { server ->
        server.keys.sortedBy { if (it.protocol == Protocol.AMNEZIAWG) 0 else 1 }.map { server to it }
    }
}
