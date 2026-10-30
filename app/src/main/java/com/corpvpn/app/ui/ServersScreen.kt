package com.corpvpn.app.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corpvpn.app.data.ConnectionState
import com.corpvpn.core.CheckList
import com.corpvpn.core.ConnectEvent
import com.corpvpn.core.ConnectMode
import com.corpvpn.core.Latency
import com.corpvpn.core.ServerProblem
import com.corpvpn.core.Server

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val servers = state.servers
    val context = LocalContext.current
    var pendingMode by remember { mutableStateOf(ConnectMode.NEAREST) }
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect(pendingMode)
    }
    val snackbar = remember { SnackbarHostState() }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Server?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Corp VPN") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (state.measuring) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else if (servers.isNotEmpty()) {
                        IconButton(onClick = { viewModel.measureAll() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Measure ping again")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImport = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add key")
            }
        },
        bottomBar = {
            if (servers.isNotEmpty()) {
                ConnectBar(
                    state = state,
                    connection = connection,
                    onConnect = { mode ->
                        // Android asks the user once to allow this app to run a VPN.
                        val consent = VpnService.prepare(context)
                        if (consent == null) {
                            viewModel.connect(mode)
                        } else {
                            pendingMode = mode
                            vpnPermission.launch(consent)
                        }
                    },
                    onDisconnect = viewModel::disconnect,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (servers.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(servers, key = { it.host }) { server ->
                    ServerCard(
                        server = server,
                        latency = state.latencies[server.host],
                        problem = state.problems[server.host],
                        isTarget = server.host == state.target?.host,
                        isPinned = server.host == state.manualHost,
                        onClick = { viewModel.toggleManual(server) },
                        onDelete = { pendingDelete = server },
                    )
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onImport = {
                viewModel.import(it)
                showImport = false
            },
            onDismiss = { showImport = false },
        )
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${server.name}?") },
            text = { Text("Its ${server.keys.size} key(s) will be deleted from this phone. You can import them again from the portal.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeServer(server)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ServerCard(
    server: Server,
    latency: Latency?,
    problem: ConnectEvent.ServerFailed?,
    isTarget: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = when {
        problem != null || latency == Latency.Unreachable -> Bad
        latency is Latency.Ms -> Good
        else -> colors.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isTarget) colors.surfaceVariant else colors.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.Dns,
                contentDescription = if (isPinned) "Chosen manually" else null,
                tint = statusColor,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    if (isTarget) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPinned) "SELECTED" else "BEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                }
                Text(
                    "${server.host} · " + server.keys.joinToString { it.protocol.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                problem?.let {
                    Text(
                        when (it.problem) {
                            ServerProblem.CONNECT_FAILED -> "Couldn't connect"
                            ServerProblem.SITES_FAILED ->
                                "Not opening: " + it.failedSites.joinToString { site -> CheckList.displayName(site) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Bad,
                    )
                }
            }
            when (latency) {
                is Latency.Ms -> Text("${latency.value} ms", style = MaterialTheme.typography.labelLarge, color = statusColor)
                Latency.Unreachable -> Text("no reply", style = MaterialTheme.typography.labelLarge, color = Bad)
                Latency.Measuring, null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove server")
            }
        }
    }
}

private val Good = Color(0xFF66BB6A)
private val Bad = Color(0xFFFF6B6B)

@Composable
private fun ConnectBar(
    state: ServersUiState,
    connection: ConnectionState,
    onConnect: (ConnectMode) -> Unit,
    onDisconnect: () -> Unit,
) {
    val target = state.target
    val colors = MaterialTheme.colorScheme
    val (status, statusColor) = when (connection) {
        is ConnectionState.Connected -> {
            val how = if (connection.verified) "check sites open" else connection.key.protocol.displayName
            "Connected to ${connection.server.name} ($how)" to Good
        }
        is ConnectionState.Connecting ->
            "Connecting to ${connection.server.name} (${connection.key.protocol.displayName})…" to colors.primary
        is ConnectionState.Checking -> "Checking sites through ${connection.server.name}…" to colors.primary
        is ConnectionState.Failed -> connection.message to Bad
        ConnectionState.Disconnected -> when {
            target == null && state.measuring -> "Measuring ping…"
            target == null -> "No server answered"
            state.manualHost != null -> "Chosen by you: ${target.name}. Tap it again for automatic choice."
            else -> "Nearest: ${target.name}"
        } to colors.onSurfaceVariant
    }
    val busy = connection is ConnectionState.Connecting || connection is ConnectionState.Checking

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
        Spacer(Modifier.height(8.dp))
        if (connection is ConnectionState.Connected || busy) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface),
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Cancel" else "Disconnect")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onConnect(ConnectMode.NEAREST) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(if (state.manualHost != null) "Connect to selected" else "Connect to nearest", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = { onConnect(ConnectMode.VERIFIED) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("Connect to verified", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No servers yet.\nGet your vpn:// key from the company portal, then tap + or open the link on this phone.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add key") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("vpn:// key or WireGuard config") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.let { text = it.text } }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
