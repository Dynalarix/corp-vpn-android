package com.corpvpn.app.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corpvpn.app.CorpVpnApp
import com.corpvpn.app.data.InstalledApp
import com.corpvpn.core.AppRules
import com.corpvpn.core.SiteEntry
import com.corpvpn.core.SiteRules
import com.corpvpn.core.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CorpVpnApp).appRules
    private val siteRepository = (application as CorpVpnApp).siteRules

    private val _siteRules = MutableStateFlow(siteRepository.load())
    val siteRules: StateFlow<SiteRules> = _siteRules

    private val _rules = MutableStateFlow(repository.load())
    val rules: StateFlow<AppRules> = _rules

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    /** Null while loading. */
    val apps: StateFlow<List<InstalledApp>?> = _apps

    init {
        viewModelScope.launch { _apps.value = withContext(Dispatchers.IO) { repository.launchableApps() } }
    }

    fun setMode(mode: SplitMode) = change { it.copy(mode = mode) }

    fun toggle(packageName: String) = change {
        it.copy(packages = if (packageName in it.packages) it.packages - packageName else it.packages + packageName)
    }

    fun setSiteMode(mode: SplitMode) = changeSites { it.copy(mode = mode) }

    fun setSiteText(text: String) = changeSites { it.copy(text = text) }

    private fun changeSites(transform: (SiteRules) -> SiteRules) {
        _siteRules.update(transform)
        siteRepository.save(_siteRules.value)
    }

    private fun change(transform: (AppRules) -> AppRules) {
        _rules.update(transform)
        repository.save(_rules.value)
    }
}

/** Split tunneling by app and by site. Changes apply the next time the VPN connects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(connected: Boolean, onBack: () -> Unit, viewModel: AppsViewModel = viewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val siteRules by viewModel.siteRules.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Split tunneling") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Apps") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sites and IPs") })
            }
            if (tab == 1) {
                SitesTab(siteRules, connected, viewModel::setSiteMode, viewModel::setSiteText)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            ModeOption(rules.mode, SplitMode.OFF, "All apps use the VPN", viewModel::setMode)
                            ModeOption(rules.mode, SplitMode.BYPASS, "Selected apps skip the VPN", viewModel::setMode)
                            ModeOption(rules.mode, SplitMode.ONLY, "Only selected apps use the VPN", viewModel::setMode)
                            Hint(
                                when (rules.mode) {
                                    SplitMode.OFF -> "Selections below are kept but not used."
                                    SplitMode.BYPASS -> "${rules.packages.size} selected. They go straight to the internet."
                                    SplitMode.ONLY -> "${rules.packages.size} selected. All other apps go straight to the internet." +
                                        if (rules.packages.isEmpty()) " Select at least one app." else ""
                                },
                            )
                            if (connected) Hint("Reconnect to apply changes.", MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Search apps") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                    val list = apps
                    if (list == null) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        // Selected apps first, so they're easy to review.
                        val shown = list
                            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
                            .sortedByDescending { it.packageName in rules.packages }
                        items(shown, key = { it.packageName }) { app ->
                            AppRow(app, checked = app.packageName in rules.packages, onToggle = { viewModel.toggle(app.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SitesTab(
    rules: SiteRules,
    connected: Boolean,
    onMode: (SplitMode) -> Unit,
    onText: (String) -> Unit,
) {
    // Held here too, so the cursor does not jump while the saved copy catches up.
    var text by rememberSaveable { mutableStateOf(rules.text) }
    val entries = remember(text) { SiteRules.parse(text) }
    val invalid = entries.filterIsInstance<SiteEntry.Invalid>()
    val count = entries.size - invalid.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ModeOption(rules.mode, SplitMode.OFF, "All sites use the VPN", onMode)
        ModeOption(rules.mode, SplitMode.BYPASS, "Listed sites skip the VPN", onMode)
        ModeOption(rules.mode, SplitMode.ONLY, "Only listed sites use the VPN", onMode)
        Hint(
            when (rules.mode) {
                SplitMode.OFF -> "The list below is kept but not used."
                SplitMode.BYPASS -> "$count in the list. They go straight to the internet."
                SplitMode.ONLY -> "$count in the list. Everything else goes straight to the internet." +
                    if (count == 0) " Add at least one site." else ""
            },
        )
        if (connected) Hint("Reconnect to apply changes.", MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onText(it)
            },
            label = { Text("One per line") },
            placeholder = { Text("example.com\n192.0.2.10\n10.0.0.0/8") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(vertical = 8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (invalid.isNotEmpty()) {
            Hint("Not a site or IP address, skipped: " + invalid.joinToString(", ") { it.line }, MaterialTheme.colorScheme.error)
        }
        Hint(
            "Write a domain (example.com), an IP address (192.0.2.10) or a range (10.0.0.0/8). " +
                "Domains are looked up when you connect, and subdomains need their own line. " +
                "Sites behind a CDN can change addresses, so if one isn't caught, add its IP range.",
        )
        if (rules.mode == SplitMode.ONLY) {
            Hint("The check sites of \"Connect to verified\" always use the VPN, so the check still tests the server.")
        }
    }
}

@Composable
private fun ModeOption(current: SplitMode, mode: SplitMode, label: String, onSelect: (SplitMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = current == mode, onClick = { onSelect(mode) }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == mode, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(vertical = 4.dp))
}
