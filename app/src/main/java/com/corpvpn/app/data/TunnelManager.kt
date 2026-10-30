package com.corpvpn.app.data

import android.content.Context
import com.corpvpn.core.ConnectEvent
import com.corpvpn.core.ConnectMode
import com.corpvpn.core.ConnectResult
import com.corpvpn.core.Connector
import com.corpvpn.core.Latency
import com.corpvpn.core.Server
import com.corpvpn.core.TunnelControl
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
    data class Checking(val server: Server, val key: VpnKey) : ConnectionState
    data class Connected(val server: Server, val key: VpnKey, val verified: Boolean) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/**
 * Runs the WireGuard / AmneziaWG tunnel through the AmneziaWG Go backend, which
 * speaks both protocols. Only one tunnel is up at a time.
 */
class TunnelManager(
    context: Context,
    private val appRules: AppRulesRepository,
    private val siteRules: SiteRulesRepository,
) {
    private val backend = GoBackend(context.applicationContext)
    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && _state.value is ConnectionState.Connected) {
                _state.value = ConnectionState.Disconnected
            }
        }
    }

    /** Site rules for the current connection; domains are resolved once per connect. */
    private var applySiteRules: (String) -> String = { it }

    private val control = object : TunnelControl {
        override suspend fun up(key: VpnKey): Boolean = withContext(Dispatchers.IO) {
            val config = Config.parse(BufferedReader(StringReader(applySiteRules(appRules.applyTo(key.config)))))
            backend.setState(tunnel, Tunnel.State.UP, config)
            awaitHandshake()
        }

        override suspend fun down() {
            withContext(Dispatchers.IO) { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
    }

    private val connector = Connector(control, HttpSiteCheck())

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** The VPN permission must already be granted (see VpnService.prepare). */
    suspend fun connect(
        mode: ConnectMode,
        target: Server,
        servers: List<Server>,
        latencies: Map<String, Latency>,
        sites: List<String>,
        onEvent: (ConnectEvent) -> Unit,
    ) {
        // Resolve while the VPN is down, so domains get the addresses the phone's own network sees.
        applySiteRules = siteRules.prepare(sites)
        val result = connector.connect(mode, target, servers, latencies, sites) { event ->
            when (event) {
                is ConnectEvent.Trying -> _state.value = ConnectionState.Connecting(event.server, event.key)
                is ConnectEvent.Checking -> _state.value = ConnectionState.Checking(event.server, event.key)
                is ConnectEvent.ServerFailed -> Unit
            }
            onEvent(event)
        }
        _state.value = when (result) {
            is ConnectResult.Connected -> ConnectionState.Connected(result.server, result.key, result.verified)
            is ConnectResult.Failed -> ConnectionState.Failed(result.message)
        }
    }

    suspend fun disconnect() {
        runCatching { control.down() }
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
