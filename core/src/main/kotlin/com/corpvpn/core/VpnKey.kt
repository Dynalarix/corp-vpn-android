package com.corpvpn.core

import kotlinx.serialization.Serializable

@Serializable
enum class Protocol(val displayName: String) {
    WIREGUARD("WireGuard"),
    AMNEZIAWG("AmneziaWG"),
}

/**
 * One imported connection key. [config] is the full wg-quick style config
 * (including AmneziaWG obfuscation fields when present) that the tunnel
 * library is given at connect time.
 */
@Serializable
data class VpnKey(
    /** Stable id derived from the config, so importing the same key twice is a no-op. */
    val id: String,
    val name: String,
    val protocol: Protocol,
    val endpointHost: String,
    val endpointPort: Int,
    val config: String,
)

/** All keys that point at the same server host, shown to the user as one server. */
data class Server(
    val host: String,
    val name: String,
    val keys: List<VpnKey>,
)

/** Groups keys by server host, keeping the order in which servers first appear. */
fun List<VpnKey>.groupByServer(): List<Server> =
    groupBy { it.endpointHost.lowercase() }
        .map { (_, keys) -> Server(host = keys.first().endpointHost, name = keys.first().name, keys = keys) }
