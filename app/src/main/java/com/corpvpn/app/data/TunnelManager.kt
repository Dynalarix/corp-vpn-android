package com.corpvpn.app.data

import android.content.Context
import com.corpvpn.core.Server
import com.corpvpn.core.VpnKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: Server, val key: VpnKey) : ConnectionState
    data class Connected(val server: Server, val key: VpnKey) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/**
 * Runs the WireGuard / AmneziaWG tunnel through the AmneziaWG Go backend, which
 * speaks both protocols. Only one tunnel is up at a time.
 */
class TunnelManager(context: Context) {
    private val backend = GoBackend(context.applicationContext)
    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && _state.value is ConnectionState.Connected) {
                _state.value = ConnectionState.Disconnected
            }
        }
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /**
     * Tries each (server, key) in order until one completes a handshake. The VPN
     * permission must already be granted (see VpnService.prepare).
     */
    suspend fun connect(candidates: List<Pair<Server, VpnKey>>) = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for ((server, key) in candidates) {
            _state.value = ConnectionState.Connecting(server, key)
            try {
                val config = Config.parse(BufferedReader(StringReader(key.config)))
                backend.setState(tunnel, Tunnel.State.UP, config)
                if (awaitHandshake()) {
                    _state.value = ConnectionState.Connected(server, key)
                    return@withContext
                }
                lastError = "${server.name} did not answer over ${key.protocol.displayName}."
            } catch (e: Exception) {
                lastError = "${server.name}: ${e.message ?: e.javaClass.simpleName}"
            }
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
        _state.value = ConnectionState.Failed(lastError ?: "No keys to connect with.")
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        _state.value = ConnectionState.Disconnected
    }

    /** WireGuard has no "connected" event; a handshake time > 0 means the server answered. */
    private suspend fun awaitHandshake(): Boolean {
        repeat(HANDSHAKE_POLLS) {
            if (backend.getLastHandshake(tunnel) > 0) return true
            delay(HANDSHAKE_POLL_MS)
        }
        return false
    }

    private companion object {
        const val TUNNEL_NAME = "corpvpn"
        const val HANDSHAKE_POLLS = 16
        const val HANDSHAKE_POLL_MS = 500L
    }
}
