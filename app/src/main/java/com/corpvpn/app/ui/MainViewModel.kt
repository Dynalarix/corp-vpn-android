package com.corpvpn.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corpvpn.app.CorpVpnApp
import com.corpvpn.app.data.CheckListRepository
import com.corpvpn.app.data.ConnectionState
import com.corpvpn.app.data.KeyRepository
import com.corpvpn.app.data.SystemLatencyProbe
import com.corpvpn.core.ConnectEvent
import com.corpvpn.core.ConnectMode
import com.corpvpn.core.KeyDecoder
import com.corpvpn.core.KeyImportException
import com.corpvpn.core.Latency
import com.corpvpn.core.LatencyProbe
import com.corpvpn.core.Server
import com.corpvpn.core.VpnKey
import com.corpvpn.core.bestServer
import com.corpvpn.core.groupByServer
import com.corpvpn.core.rankServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServersUiState(
    /** Best first. */
    val servers: List<Server> = emptyList(),
    val latencies: Map<String, Latency> = emptyMap(),
    /** The server Connect will use: the manual pick if any, else the best by ping. */
    val target: Server? = null,
    val manualHost: String? = null,
    val measuring: Boolean = false,
    /** Servers that failed during the last connect attempt, and why. Cleared by a new ping round. */
    val problems: Map<String, ConnectEvent.ServerFailed> = emptyMap(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KeyRepository(application)
    private val probe: LatencyProbe = SystemLatencyProbe()
    private val tunnels = (application as CorpVpnApp).tunnels
    private val checkList = CheckListRepository(application)

    val connection: StateFlow<ConnectionState> = tunnels.state

    private val keys = MutableStateFlow<List<VpnKey>>(emptyList())
    private val latencies = MutableStateFlow<Map<String, Latency>>(emptyMap())
    private val manualHost = MutableStateFlow<String?>(null)
    private val measuring = MutableStateFlow(false)
    private val problems = MutableStateFlow<Map<String, ConnectEvent.ServerFailed>>(emptyMap())
    private var measureJob: Job? = null
    private var connectJob: Job? = null

    val state: StateFlow<ServersUiState> =
        combine(keys, latencies, manualHost, measuring, problems) { keys, latencies, manual, measuring, problems ->
            val servers = keys.groupByServer()
            val manualServer = servers.firstOrNull { it.host == manual }
            ServersUiState(
                servers = rankServers(servers, latencies),
                latencies = latencies,
                target = manualServer ?: bestServer(servers, latencies),
                manualHost = manualServer?.host,
                measuring = measuring,
                problems = problems,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ServersUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            keys.value = withContext(Dispatchers.IO) { repository.load() }
            measureAll()
        }
    }

    /** Pings every server in parallel. A newer call cancels one still running. */
    fun measureAll(): Job {
        measureJob?.cancel()
        return viewModelScope.launch {
            val hosts = keys.value.groupByServer().map { it.host }
            if (hosts.isEmpty()) return@launch
            measuring.value = true
            problems.value = emptyMap()
            latencies.update { old -> hosts.associateWith { old[it] ?: Latency.Measuring } }
            try {
                measure(hosts)
            } finally {
                measuring.value = false
            }
        }.also { measureJob = it }
    }

    private suspend fun measure(hosts: List<String>) = coroutineScope {
        hosts.map { host ->
            async(Dispatchers.IO) {
                val ms = probe.measure(host)
                latencies.update { it + (host to (ms?.let(Latency::Ms) ?: Latency.Unreachable)) }
            }
        }.awaitAll()
    }

    /** Tapping a server pins it; tapping the pinned one goes back to automatic choice. */
    fun toggleManual(server: Server) {
        manualHost.update { if (it == server.host) null else server.host }
    }

    /**
     * [ConnectMode.NEAREST]: the chosen server, else the lowest ping.
     * [ConnectMode.VERIFIED]: the same, but the check sites must open through the VPN,
     * otherwise the next server is tried.
     * The VPN permission must be granted before this is called.
     */
    fun connect(mode: ConnectMode) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            // Ping through an open tunnel would be meaningless, so start from a clean state.
            if (connection.value !is ConnectionState.Disconnected) tunnels.disconnect()
            if (state.value.manualHost == null) measureAll().join()
            val current = state.value
            val target = current.target
            if (target == null) {
                _messages.send("No server answered. Check your internet connection and try again.")
                return@launch
            }
            val sites = withContext(Dispatchers.IO) { checkList.sites() }
            problems.value = emptyMap()
            tunnels.connect(mode, target, current.servers, current.latencies, sites) { event ->
                if (event is ConnectEvent.ServerFailed) problems.update { it + (event.server.host to event) }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        viewModelScope.launch { tunnels.disconnect() }
    }

    fun import(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val decoded = try {
                KeyDecoder.decode(input)
            } catch (e: KeyImportException) {
                _messages.send(e.message ?: "Couldn't import the key.")
                return@launch
            }
            val existing = keys.value.map { it.id }.toSet()
            val added = decoded.filter { it.id !in existing }
            if (added.isEmpty()) {
                _messages.send("This key is already added.")
                return@launch
            }
            val updated = keys.value + added
            repository.save(updated)
            keys.value = updated
            _messages.send("Added ${added.first().name} (${added.joinToString { it.protocol.displayName }}).")
            withContext(Dispatchers.Main) { measureAll() }
        }
    }

    fun removeServer(server: Server) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = server.keys.map { it.id }.toSet()
            val updated = keys.value.filter { it.id !in ids }
            repository.save(updated)
            keys.value = updated
            latencies.update { it - server.host }
            manualHost.update { if (it == server.host) null else it }
        }
    }
}
