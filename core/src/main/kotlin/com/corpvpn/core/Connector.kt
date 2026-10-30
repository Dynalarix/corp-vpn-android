package com.corpvpn.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectMode {
    /** Lowest ping (or the server the user picked); next server only if the tunnel won't come up. */
    NEAREST,

    /** Like [NEAREST], but the check sites must open through the tunnel or the next server is tried. */
    VERIFIED,
}

/** Brings the VPN tunnel up and down. Implemented by the app over the AmneziaWG backend. */
interface TunnelControl {
    /** Starts the tunnel with [key]. True once the server answered the handshake. May throw on bad configs. */
    suspend fun up(key: VpnKey): Boolean
    suspend fun down()
}

/** Tries to open one check site over the current network (the tunnel, once it is up). */
fun interface SiteCheck {
    suspend fun reachable(url: String): Boolean
}

enum class ServerProblem { CONNECT_FAILED, SITES_FAILED }

/** Progress while [Connector.connect] works through the servers. */
sealed interface ConnectEvent {
    data class Trying(val server: Server, val key: VpnKey) : ConnectEvent
    data class Checking(val server: Server, val key: VpnKey) : ConnectEvent
    data class ServerFailed(
        val server: Server,
        val problem: ServerProblem,
        val failedSites: List<String> = emptyList(),
    ) : ConnectEvent
}

sealed interface ConnectResult {
    data class Connected(val server: Server, val key: VpnKey, val verified: Boolean) : ConnectResult
    data class Failed(val message: String) : ConnectResult
}

class Connector(
    private val tunnel: TunnelControl,
    private val siteCheck: SiteCheck,
    /** How long all check sites get to answer through a fresh tunnel. */
    private val checkWindowMs: Long = 12_000,
    private val retryDelayMs: Long = 1_000,
) {
    suspend fun connect(
        mode: ConnectMode,
        target: Server,
        servers: List<Server>,
        latencies: Map<String, Latency>,
        sites: List<String>,
        onEvent: (ConnectEvent) -> Unit = {},
    ): ConnectResult {
        val verify = mode == ConnectMode.VERIFIED && sites.isNotEmpty()
        val keysByServer = LinkedHashMap<Server, MutableList<VpnKey>>()
        for ((server, key) in connectionCandidates(target, servers, latencies)) {
            keysByServer.getOrPut(server) { mutableListOf() } += key
        }

        var lastError: String? = null
        val blockedSites = linkedSetOf<String>()
        for ((server, keys) in keysByServer) {
            val key = keys.firstOrNull { key ->
                onEvent(ConnectEvent.Trying(server, key))
                try {
                    tunnel.up(key).also { ok -> if (!ok) tunnel.down() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    runCatching { tunnel.down() }
                    false
                }
            }
            if (key == null) {
                onEvent(ConnectEvent.ServerFailed(server, ServerProblem.CONNECT_FAILED))
                continue
            }
            if (!verify) return ConnectResult.Connected(server, key, verified = false)

            onEvent(ConnectEvent.Checking(server, key))
            val failed = failingSites(sites)
            if (failed.isEmpty()) return ConnectResult.Connected(server, key, verified = true)
            onEvent(ConnectEvent.ServerFailed(server, ServerProblem.SITES_FAILED, failed))
            blockedSites += failed
            tunnel.down()
        }

        return ConnectResult.Failed(
            when {
                blockedSites.isNotEmpty() ->
                    "No server can open all check sites. Not working: ${blockedSites.joinToString { CheckList.displayName(it) }}."
                lastError != null -> "Couldn't connect to any server. Last error: $lastError"
                else -> "Couldn't connect to any server. None of them answered."
            },
        )
    }

    private suspend fun failingSites(sites: List<String>): List<String> = coroutineScope {
        sites.map { site -> async { site to opensWithinWindow(site) } }
            .awaitAll()
            .filterNot { it.second }
            .map { it.first }
    }

    /** A fresh tunnel can take a moment to carry traffic, so keep retrying until the window closes. */
    private suspend fun opensWithinWindow(site: String): Boolean =
        withTimeoutOrNull(checkWindowMs) {
            while (!siteCheck.reachable(site)) delay(retryDelayMs)
            true
        } ?: false
}
