package com.corpvpn.app.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corpvpn.app.data.CheckListSettings
import com.corpvpn.core.CheckList

/** Edit the sites "Connect to verified" checks, and the link to a shared list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckListScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.checkListSettings.collectAsStateWithLifecycle()
    val updating by viewModel.checkListUpdating.collectAsStateWithLifecycle()
    val loaded = settings ?: return

    var manualText by rememberSaveable { mutableStateOf(loaded.manualText) }
    var url by rememberSaveable { mutableStateOf(loaded.url) }
    val edited = manualText != loaded.manualText || url.trim() != loaded.url

    fun saveAndBack() {
        if (edited) viewModel.saveCheckList(manualText, url)
        onBack()
    }
    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Check sites") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and go back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint(
                "\"Connect to verified\" only stays on a server if all of these sites open through it. " +
                    "One site per line, for example api.telegram.org. Lines starting with # are ignored.",
            )
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                label = { Text("Your sites") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Spacer(Modifier.size(4.dp))
            Text("Shared list", style = MaterialTheme.typography.titleSmall)
            Hint("Optional link to a text file with one site per line. It is downloaded on start and before every verified connection, and added to your sites.")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link to list (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (edited) viewModel.saveCheckList(manualText, url) else viewModel.updateCheckListNow()
                    },
                    enabled = url.isNotBlank() && !updating,
                ) { Text("Update now") }
                Spacer(Modifier.width(12.dp))
                if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            DownloadStatus(loaded)

            Spacer(Modifier.size(4.dp))
            Text("Checked sites (${loaded.sites.size})", style = MaterialTheme.typography.titleSmall)
            if (edited) Hint("Save to see your changes here.")
            Text(
                loaded.sites.joinToString("\n") { CheckList.displayName(it) }.ifEmpty { "None. Verified works like nearest." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = ::saveAndBack, enabled = edited, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun DownloadStatus(settings: CheckListSettings) {
    if (settings.url.isEmpty()) return
    settings.lastError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    val count = CheckList.parse(settings.downloadedText.orEmpty()).size
    Hint(
        settings.downloadedAt?.let {
            "Downloaded $count sites " + DateUtils.getRelativeTimeSpanString(it).toString().lowercase() + "."
        } ?: "Not downloaded yet.",
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
